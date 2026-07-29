package Test.unit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import server.auth.User;
import server.dispatch.handlers.RoomCancelHandler;
import server.dispatch.handlers.RoomCreateHandler;
import server.dispatch.handlers.RoomJoinHandler;
import server.net.PlayerSession;
import server.rating.EloRatingService;
import server.rooms.Room;
import server.rooms.RoomManager;
import shared.eventbus.EventBus;
import shared.protocol.Message;
import shared.protocol.MessageCodec;
import shared.protocol.MessageType;
import shared.protocol.payload.RoomActionPayload;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

public class RoomJoinHandlerTest {

    private static final class InMemoryUserRepository implements server.auth.UserRepository {
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

    private RoomManager roomManager;
    private Room startedRoom;

    @AfterEach
    public void tearDown() {
        if (startedRoom != null && startedRoom.getGameSession() != null) {
            startedRoom.getGameSession().handleResign(startedRoom.getUser('w').getId());
        }
    }

    private PlayerSession loggedInSession(User user, List<String> sent) throws Exception {
        PlayerSession session = new PlayerSession(TestSupport.fakeWebSocket(true, sent), TestSupport.newActivityLogger());
        session.setUser(user);
        return session;
    }

    private Message roomMessage(MessageType type, String roomId) {
        return MessageCodec.decodeEnvelope(MessageCodec.encode(type, new RoomActionPayload(roomId)));
    }

    @Test
    public void testJoinExistingRoomAssignsBlackAndStartsGame() throws Exception {
        roomManager = TestSupport.newRoomManagerWithLocalShard(new EventBus(), new EloRatingService(), new InMemoryUserRepository());
        RoomCreateHandler createHandler = new RoomCreateHandler(roomManager);
        RoomJoinHandler joinHandler = new RoomJoinHandler(roomManager);

        User alice = new User(1, "alice", 1200);
        User bob = new User(2, "bob", 1200);
        createHandler.handle(loggedInSession(alice, new ArrayList<>()), roomMessage(MessageType.ROOM_CREATE, "GAMEROOM"));

        List<String> bobSent = new ArrayList<>();
        PlayerSession bobSession = loggedInSession(bob, bobSent);
        joinHandler.handle(bobSession, roomMessage(MessageType.ROOM_JOIN, "GAMEROOM"));

        assertEquals("GAMEROOM", bobSession.getCurrentRoomId());
        // Not necessarily bobSent.get(0): starting the game session now also synchronously relays a
        // GAME_STARTED GAME_EVENT to every room member (server.shard.ShardGameEventRelay, via the
        // local Shard this test runs against - see TestSupport.newRoomManagerWithLocalShard) before
        // RoomJoinHandler gets to send its own ROOM_JOINED confirmation. Both arrive; order between
        // them isn't a contract worth asserting on, so check "was it sent at all" instead of "was it first".
        assertTrue(bobSent.stream().anyMatch(s -> s.contains("BLACK")),
                "expected a ROOM_JOINED message containing the assigned role somewhere in what bob received: " + bobSent);

        startedRoom = roomManager.getRoom("GAMEROOM").orElseThrow();
        assertNotNull(startedRoom.getGameSession());
    }

    @Test
    public void testJoinUnknownRoomSendsError() throws Exception {
        roomManager = TestSupport.newRoomManagerWithLocalShard(new EventBus(), new EloRatingService(), new InMemoryUserRepository());
        RoomJoinHandler joinHandler = new RoomJoinHandler(roomManager);

        List<String> sent = new ArrayList<>();
        User bob = new User(2, "bob", 1200);
        PlayerSession session = loggedInSession(bob, sent);
        joinHandler.handle(session, roomMessage(MessageType.ROOM_JOIN, "NOPE"));

        assertNull(session.getCurrentRoomId());
        assertTrue(sent.get(0).contains("not found") || sent.get(0).contains("ERROR"));
    }

    @Test
    public void testRoomCancelBeforeGameStartsDetachesCreator() throws Exception {
        roomManager = TestSupport.newRoomManagerWithLocalShard(new EventBus(), new EloRatingService(), new InMemoryUserRepository());
        RoomCreateHandler createHandler = new RoomCreateHandler(roomManager);
        RoomCancelHandler cancelHandler = new RoomCancelHandler(roomManager);

        User alice = new User(1, "alice", 1200);
        PlayerSession session = loggedInSession(alice, new ArrayList<>());
        createHandler.handle(session, roomMessage(MessageType.ROOM_CREATE, "TOCANCEL"));

        assertDoesNotThrow(() -> cancelHandler.handle(session, MessageCodec.decodeEnvelope(
                MessageCodec.encode(MessageType.ROOM_CANCEL, shared.protocol.payload.EmptyPayload.INSTANCE))));
        assertNull(session.getCurrentRoomId());
    }

    @Test
    public void testRoomCancelWithNoCurrentRoomIsANoOp() throws Exception {
        roomManager = TestSupport.newRoomManagerWithLocalShard(new EventBus(), new EloRatingService(), new InMemoryUserRepository());
        RoomCancelHandler cancelHandler = new RoomCancelHandler(roomManager);

        User alice = new User(1, "alice", 1200);
        PlayerSession session = loggedInSession(alice, new ArrayList<>());
        assertDoesNotThrow(() -> cancelHandler.handle(session, MessageCodec.decodeEnvelope(
                MessageCodec.encode(MessageType.ROOM_CANCEL, shared.protocol.payload.EmptyPayload.INSTANCE))));
    }
}
