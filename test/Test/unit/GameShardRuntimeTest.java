package Test.unit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import server.allocator.ShardSelector;
import server.auth.User;
import server.gateway.GatewayRoomRegistry;
import server.gateway.GatewayRoomSession;
import server.history.GameHistoryRecorder;
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
import shared.metrics.MetricsRegistry;
import shared.protocol.MessageType;
import shared.protocol.payload.GameOverPayload;
import shared.protocol.payload.RoutedUpdatePayload;
import shared.protocol.payload.StateUpdatePayload;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

public class GameShardRuntimeTest {

    private static final String SHARD_ID = "shard-1";

    private static final class RecordingSink implements MessageSink {
        final java.util.List<Object> received = new java.util.concurrent.CopyOnWriteArrayList<>();
        @Override public void send(MessageType type, Object payload) { received.add(payload); }
        boolean receivedAny(Class<?> clazz) { return received.stream().anyMatch(clazz::isInstance); }
    }

    private RoomManager roomManager;
    private GatewayRoomRegistry roomRegistry;
    private String roomId;
    private User alice;
    private User bob;

    @AfterEach
    public void tearDown() {
        if (roomId != null && roomRegistry != null) {
            roomRegistry.get(roomId).ifPresent(session -> {
                if (!session.isOver()) session.forceResignDueToDisconnect(alice.getId());
            });
        }
    }

    private RoomManager newMonolithRoomManagerOnSharedBus(MessageBus bus, EventBus eventBus) {
        roomRegistry = new GatewayRoomRegistry(bus);
        return new RoomManager(eventBus, bus, new ShardSelector(java.util.List.of(SHARD_ID)));
    }

    @Test
    public void testFullGameEndToEndThroughSingleExternalShard() {
        MessageBus bus = new InMemoryMessageBus();
        MetricsRegistry metricsRegistry = new MetricsRegistry();
        GameShardRuntime shard = new GameShardRuntime(bus, new EventBus(), new EloRatingService(),
                (userId, newElo) -> { }, GameHistoryRecorder.NO_OP, SHARD_ID, metricsRegistry);
        shard.start();

        EventBus monolithEventBus = new EventBus();
        roomManager = newMonolithRoomManagerOnSharedBus(bus, monolithEventBus);
        alice = new User(1, "alice", 1200);
        bob = new User(2, "bob", 1200);
        Room room = roomManager.createRoomForMatch(alice, bob);
        roomId = room.getId();

        GatewayRoomSession session = roomRegistry.get(roomId).orElseThrow();
        RecordingSink aliceSink = new RecordingSink();
        session.addLocalMember(alice.getId(), aliceSink);

        assertTrue(roomManager.getActiveRoomForUser(alice.getId()).isPresent(),
                "room.allocated should have reached the Shard and a real GameSession should be running");

        assertTrue(session.handleMove(alice.getId(), new models.Position(6, 4), new models.Position(4, 4)),
                "legal move by its owner, round-tripped through the Shard, should be accepted");

        assertFalse(session.handleMove(alice.getId(), new models.Position(1, 4), new models.Position(3, 4)),
                "moving the opponent's piece should be rejected, exactly like the in-process engine used to");

        session.forceResignDueToDisconnect(alice.getId());

        assertTrue(session.isOver(), "GAME_OVER relayed back from the Shard should flip isOver()");
        assertTrue(aliceSink.receivedAny(GameOverPayload.class), "the client-facing sink should have received GAME_OVER");
        assertTrue(roomManager.getActiveRoomForUser(alice.getId()).isEmpty(),
                "RoomManager must re-publish GameEndedEvent on the monolith's own EventBus "
                        + "so its activeRoomByUserId cleanup (unchanged since before the split) still fires");

        String renderedMetrics = metricsRegistry.renderPrometheusText();
        assertTrue(renderedMetrics.contains("shard_rooms_allocated_total 1"));
        assertTrue(renderedMetrics.contains("shard_active_games 1"));
        assertTrue(renderedMetrics.contains("shard_commands_total"));
    }

    @Test
    public void testSpectatorJoiningMidGameIsRegisteredWithTheRemoteSession() {
        MessageBus bus = new InMemoryMessageBus();
        GameShardRuntime shard = new GameShardRuntime(bus, new EventBus(), new EloRatingService(),
                (userId, newElo) -> { }, GameHistoryRecorder.NO_OP, SHARD_ID, new MetricsRegistry());
        shard.start();

        roomManager = newMonolithRoomManagerOnSharedBus(bus, new EventBus());
        alice = new User(1, "alice", 1200);
        bob = new User(2, "bob", 1200);
        Room room = roomManager.createRoomForMatch(alice, bob);
        roomId = room.getId();
        GatewayRoomSession session = roomRegistry.get(roomId).orElseThrow();

        User carol = new User(3, "carol", 1200);
        RoomManager.JoinResult joined = roomManager.joinRoom(room.getId(), carol);

        assertTrue(joined.success);
        assertEquals(Room.Role.SPECTATOR, joined.role);
        session.notifyMemberJoined(carol.getId(), Room.Role.SPECTATOR);

        assertTrue(session.handleMove(alice.getId(), new models.Position(6, 4), new models.Position(4, 4)));
    }

    @Test
    public void testRemoteHandleMoveWithNoShardListeningIsRejectedNotThrown() {
        MessageBus bus = new InMemoryMessageBus();
        roomManager = newMonolithRoomManagerOnSharedBus(bus, new EventBus());
        alice = new User(1, "alice", 1200);
        bob = new User(2, "bob", 1200);
        Room room = roomManager.createRoomForMatch(alice, bob);
        roomId = room.getId();
        GatewayRoomSession session = roomRegistry.get(roomId).orElseThrow();

        boolean accepted = assertDoesNotThrow(() ->
                session.handleMove(alice.getId(), new models.Position(6, 4), new models.Position(4, 4)));

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
