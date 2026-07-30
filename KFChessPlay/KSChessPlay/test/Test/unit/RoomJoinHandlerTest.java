package Test.unit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import server.auth.User;
import server.dispatch.handlers.RoomCancelHandler;
import server.dispatch.handlers.RoomCreateHandler;
import server.dispatch.handlers.RoomJoinHandler;
import server.net.PlayerSession;
import server.rating.EloRatingService;
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

    private TestSupport.GatewayHarness harness;
    private String startedRoomId;
    private long startedRoomWhiteUserId;

    @AfterEach
    public void tearDown() {
        if (startedRoomId != null) {
            harness.forceResign(startedRoomId, startedRoomWhiteUserId);
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
        harness = TestSupport.newGatewayHarness(new EventBus(), new EloRatingService(), new InMemoryUserRepository());
        RoomCreateHandler createHandler = new RoomCreateHandler(harness.roomLifecycle, harness.roomRegistry);
        RoomJoinHandler joinHandler = new RoomJoinHandler(harness.roomLifecycle, harness.roomRegistry);

        User alice = new User(1, "alice", 1200);
        User bob = new User(2, "bob", 1200);
        createHandler.handle(loggedInSession(alice, new ArrayList<>()), roomMessage(MessageType.ROOM_CREATE, "GAMEROOM"));

        List<String> bobSent = new ArrayList<>();
        PlayerSession bobSession = loggedInSession(bob, bobSent);
        joinHandler.handle(bobSession, roomMessage(MessageType.ROOM_JOIN, "GAMEROOM"));

        assertEquals("GAMEROOM", bobSession.getCurrentRoomId());

        assertTrue(bobSent.stream().anyMatch(s -> s.contains("BLACK")),
                "expected a ROOM_JOINED message containing the assigned role somewhere in what bob received: " + bobSent);

        startedRoomId = "GAMEROOM";
        startedRoomWhiteUserId = alice.getId();
        assertTrue(harness.roomManager.getRoom("GAMEROOM").orElseThrow().isStarted());
        assertTrue(harness.roomRegistry.get("GAMEROOM").isPresent());
    }

    @Test
    public void testJoinUnknownRoomSendsError() throws Exception {
        harness = TestSupport.newGatewayHarness(new EventBus(), new EloRatingService(), new InMemoryUserRepository());
        RoomJoinHandler joinHandler = new RoomJoinHandler(harness.roomLifecycle, harness.roomRegistry);

        List<String> sent = new ArrayList<>();
        User bob = new User(2, "bob", 1200);
        PlayerSession session = loggedInSession(bob, sent);
        joinHandler.handle(session, roomMessage(MessageType.ROOM_JOIN, "NOPE"));

        assertNull(session.getCurrentRoomId());
        assertTrue(sent.get(0).contains("not found") || sent.get(0).contains("ERROR"));
    }

    @Test
    public void testRoomCancelBeforeGameStartsDetachesCreator() throws Exception {
        harness = TestSupport.newGatewayHarness(new EventBus(), new EloRatingService(), new InMemoryUserRepository());
        RoomCreateHandler createHandler = new RoomCreateHandler(harness.roomLifecycle, harness.roomRegistry);
        RoomCancelHandler cancelHandler = new RoomCancelHandler(harness.roomLifecycle);

        User alice = new User(1, "alice", 1200);
        PlayerSession session = loggedInSession(alice, new ArrayList<>());
        createHandler.handle(session, roomMessage(MessageType.ROOM_CREATE, "TOCANCEL"));

        assertDoesNotThrow(() -> cancelHandler.handle(session, MessageCodec.decodeEnvelope(
                MessageCodec.encode(MessageType.ROOM_CANCEL, shared.protocol.payload.EmptyPayload.INSTANCE))));
        assertNull(session.getCurrentRoomId());
    }

    @Test
    public void testRoomCancelWithNoCurrentRoomIsANoOp() throws Exception {
        harness = TestSupport.newGatewayHarness(new EventBus(), new EloRatingService(), new InMemoryUserRepository());
        RoomCancelHandler cancelHandler = new RoomCancelHandler(harness.roomLifecycle);

        User alice = new User(1, "alice", 1200);
        PlayerSession session = loggedInSession(alice, new ArrayList<>());
        assertDoesNotThrow(() -> cancelHandler.handle(session, MessageCodec.decodeEnvelope(
                MessageCodec.encode(MessageType.ROOM_CANCEL, shared.protocol.payload.EmptyPayload.INSTANCE))));
    }
}
