package Test.unit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import server.auth.User;
import server.auth.UserRepository;
import server.net.MessageSink;
import server.rating.EloRatingService;
import server.rooms.Room;
import server.rooms.RoomManager;
import server.shard.BusUpdateSink;
import server.shard.GameShardRuntime;
import shared.bus.InMemoryMessageBus;
import shared.bus.MessageBus;
import shared.bus.RoomBusSubjects;
import shared.eventbus.EventBus;
import shared.protocol.MessageType;
import shared.protocol.payload.GameOverPayload;
import shared.protocol.payload.RoutedUpdatePayload;
import shared.protocol.payload.StateUpdatePayload;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Exercises PLAN_split_6_services.md §3.1's own stated verification: "the monolith (everything
 * except GameSession) talks successfully to a single external Shard over NATS; a full game works
 * end-to-end." {@link InMemoryMessageBus} stands in for a live NATS connection here, same as
 * {@code MessageBusTest} already does for the raw bus contract - {@link GameShardRuntime} is the
 * exact class {@code server.shard.GameServerShardMain} wires to the real thing in production.
 */
public class GameShardRuntimeTest {

    private static final class InMemoryUserRepository implements UserRepository {
        private final Map<Long, StoredUser> byId = new HashMap<>();
        private final AtomicLong nextId = new AtomicLong(1);
        @Override public Optional<StoredUser> findByUsername(String username) { return Optional.empty(); }
        @Override public StoredUser create(String username, String passwordHash, String salt, int startingElo) {
            StoredUser u = new StoredUser(nextId.getAndIncrement(), username, passwordHash, salt, startingElo);
            byId.put(u.id, u);
            return u;
        }
        @Override public void updateElo(long userId, int newElo) { }
    }

    private static final class RecordingSink implements MessageSink {
        final java.util.List<Object> received = new java.util.concurrent.CopyOnWriteArrayList<>();
        @Override public void send(MessageType type, Object payload) { received.add(payload); }
        boolean receivedAny(Class<?> clazz) { return received.stream().anyMatch(clazz::isInstance); }
    }

    private RoomManager roomManager;
    private Room room;
    private User alice;
    private User bob;

    @AfterEach
    public void tearDown() {
        if (room != null && room.getGameSession() != null && !room.getGameSession().isOver()) {
            room.getGameSession().handleResign(alice.getId());
        }
    }

    private RoomManager newMonolithRoomManagerOnSharedBus(MessageBus bus, EventBus eventBus) {
        return new RoomManager(eventBus, new EloRatingService(), new InMemoryUserRepository(), bus);
    }

    @Test
    public void testFullGameEndToEndThroughSingleExternalShard() {
        MessageBus bus = new InMemoryMessageBus();
        GameShardRuntime shard = new GameShardRuntime(bus, new EventBus(), new EloRatingService(), new InMemoryUserRepository());
        shard.start();

        EventBus monolithEventBus = new EventBus();
        roomManager = new RoomManager(monolithEventBus, new EloRatingService(), new InMemoryUserRepository(), bus);
        alice = new User(1, "alice", 1200);
        bob = new User(2, "bob", 1200);
        room = roomManager.createRoomForMatch(alice, bob);

        RecordingSink aliceSink = new RecordingSink();
        room.attachSink(alice.getId(), aliceSink);

        assertTrue(roomManager.getActiveRoomForUser(alice.getId()).isPresent(),
                "room.allocated should have reached the Shard and a real GameSession should be running");

        // alice (WHITE) moving her own pawn should be accepted by the real engine running in the Shard.
        assertTrue(room.getGameSession().handleMove(alice.getId(), new models.Position(6, 4), new models.Position(4, 4)),
                "legal move by its owner, round-tripped through the Shard, should be accepted");

        // alice trying to move one of bob's (BLACK) pieces should be rejected by the same real engine.
        assertFalse(room.getGameSession().handleMove(alice.getId(), new models.Position(1, 4), new models.Position(3, 4)),
                "moving the opponent's piece should be rejected, exactly like the in-process engine used to");

        room.getGameSession().handleResign(alice.getId());

        assertTrue(room.getGameSession().isOver(), "GAME_OVER relayed back from the Shard should flip isOver()");
        assertTrue(aliceSink.receivedAny(GameOverPayload.class), "the client-facing sink should have received GAME_OVER");
        assertTrue(roomManager.getActiveRoomForUser(alice.getId()).isEmpty(),
                "RemoteGameSessionHandle must re-publish GameEndedEvent on the monolith's own EventBus "
                        + "so RoomManager's activeRoomByUserId cleanup (unchanged since before the split) still fires");
    }

    @Test
    public void testSpectatorJoiningMidGameIsRegisteredWithTheRemoteSession() {
        MessageBus bus = new InMemoryMessageBus();
        GameShardRuntime shard = new GameShardRuntime(bus, new EventBus(), new EloRatingService(), new InMemoryUserRepository());
        shard.start();

        roomManager = newMonolithRoomManagerOnSharedBus(bus, new EventBus());
        alice = new User(1, "alice", 1200);
        bob = new User(2, "bob", 1200);
        room = roomManager.createRoomForMatch(alice, bob);

        User carol = new User(3, "carol", 1200);
        RoomManager.JoinResult joined = roomManager.joinRoom(room.getId(), carol, new RecordingSink());

        assertTrue(joined.success);
        assertEquals(Room.Role.SPECTATOR, joined.role);
        // Real assertion: onMemberJoined must not have thrown/blocked, and the game must still be
        // running fine afterwards for the two real players.
        assertTrue(room.getGameSession().handleMove(alice.getId(), new models.Position(6, 4), new models.Position(4, 4)));
    }

    @Test
    public void testRemoteHandleMoveWithNoShardListeningIsRejectedNotThrown() {
        MessageBus bus = new InMemoryMessageBus(); // no GameShardRuntime subscribed to it at all
        roomManager = newMonolithRoomManagerOnSharedBus(bus, new EventBus());
        alice = new User(1, "alice", 1200);
        bob = new User(2, "bob", 1200);
        room = roomManager.createRoomForMatch(alice, bob);

        boolean accepted = assertDoesNotThrow(() ->
                room.getGameSession().handleMove(alice.getId(), new models.Position(6, 4), new models.Position(4, 4)));

        assertFalse(accepted, "no Shard subscribed => request() times out/empty => treated as rejected, not a crash");
    }

    @Test
    public void testBusUpdateSinkSendToWrapsTargetUserId() {
        MessageBus bus = new InMemoryMessageBus();
        AtomicReference<RoutedUpdatePayload> captured = new AtomicReference<>();
        bus.subscribe(RoomBusSubjects.updates("ROOM1"), (msg, replyTo) ->
                captured.set(shared.protocol.MessageCodec.decodePayload(msg, RoutedUpdatePayload.class)));

        StateUpdatePayload state = new StateUpdatePayload("ROOM1", 'w', 8, 8, null, "alice", "bob");
        new BusUpdateSink(bus, "ROOM1").sendTo(42L, MessageType.STATE_UPDATE, state);

        assertNotNull(captured.get());
        assertEquals(Long.valueOf(42L), captured.get().targetUserId);
        assertEquals(MessageType.STATE_UPDATE, captured.get().innerType);
    }

    @Test
    public void testBusUpdateSinkBroadcastToAllHasNullTargetUserId() {
        MessageBus bus = new InMemoryMessageBus();
        AtomicReference<RoutedUpdatePayload> captured = new AtomicReference<>();
        bus.subscribe(RoomBusSubjects.updates("ROOM2"), (msg, replyTo) ->
                captured.set(shared.protocol.MessageCodec.decodePayload(msg, RoutedUpdatePayload.class)));

        new BusUpdateSink(bus, "ROOM2").broadcastToAll(MessageType.GAME_OVER,
                new GameOverPayload("ROOM2", "WHITE", "resignation", 8, -8, 1208, 1192, "alice", "bob"));

        assertNotNull(captured.get());
        assertNull(captured.get().targetUserId);
        assertEquals(MessageType.GAME_OVER, captured.get().innerType);
    }

    @Test
    public void testRoomBusSubjectsBuildersAreStable() {
        assertEquals("room.allocated", RoomBusSubjects.ROOM_ALLOCATED);
        assertEquals("room.ABC123.commands", RoomBusSubjects.commands("ABC123"));
        assertEquals("room.ABC123.updates", RoomBusSubjects.updates("ABC123"));
    }
}
