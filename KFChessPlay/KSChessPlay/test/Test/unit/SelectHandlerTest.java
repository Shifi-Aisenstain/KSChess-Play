package Test.unit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import server.auth.User;
import server.dispatch.handlers.SelectHandler;
import server.net.PlayerSession;
import server.rating.EloRatingService;
import server.rooms.Room;
import shared.eventbus.EventBus;
import shared.protocol.Message;
import shared.protocol.MessageCodec;
import shared.protocol.MessageType;
import shared.protocol.payload.SelectPayload;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

public class SelectHandlerTest {

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
    private Room room;
    private User alice;
    private User bob;

    private PlayerSession session(User user, List<String> sent) throws Exception {
        PlayerSession s = new PlayerSession(TestSupport.fakeWebSocket(true, sent), TestSupport.newActivityLogger());
        s.setUser(user);
        s.setCurrentRoomId(room.getId());
        return s;
    }

    private void startGame() {
        harness = TestSupport.newGatewayHarness(new EventBus(), new EloRatingService(), new InMemoryUserRepository());
        alice = new User(1, "alice", 1200);
        bob = new User(2, "bob", 1200);
        room = harness.roomManager.createRoomForMatch(alice, bob);
    }

    @AfterEach
    public void tearDown() {
        if (room != null) {
            harness.forceResign(room.getId(), alice.getId());
        }
    }

    @Test
    public void testSelectValidSquareDoesNotThrow() throws Exception {
        startGame();
        SelectHandler handler = new SelectHandler(harness.roomRegistry);
        Message message = MessageCodec.decodeEnvelope(
                MessageCodec.encode(MessageType.SELECT, new SelectPayload(true, 6, 4)));

        assertDoesNotThrow(() -> handler.handle(session(alice, new ArrayList<>()), message));
    }

    @Test
    public void testDeselectDoesNotThrow() throws Exception {
        startGame();
        SelectHandler handler = new SelectHandler(harness.roomRegistry);
        Message message = MessageCodec.decodeEnvelope(
                MessageCodec.encode(MessageType.SELECT, new SelectPayload(false, 0, 0)));

        assertDoesNotThrow(() -> handler.handle(session(alice, new ArrayList<>()), message));
    }

    @Test
    public void testSelectWithoutActiveGameSendsError() throws Exception {
        startGame();
        SelectHandler handler = new SelectHandler(harness.roomRegistry);
        List<String> sent = new ArrayList<>();
        PlayerSession noRoomSession = new PlayerSession(TestSupport.fakeWebSocket(true, sent), TestSupport.newActivityLogger());
        noRoomSession.setUser(new User(99, "carol", 1200));

        handler.handle(noRoomSession, MessageCodec.decodeEnvelope(
                MessageCodec.encode(MessageType.SELECT, new SelectPayload(true, 0, 0))));

        assertTrue(sent.get(0).contains("not in a room"));
    }

    @Test
    public void testSelectWithRoomIdForANonexistentRoomSendsGameNotStartedError() throws Exception {
        startGame();
        SelectHandler handler = new SelectHandler(harness.roomRegistry);
        List<String> sent = new ArrayList<>();
        PlayerSession session = new PlayerSession(TestSupport.fakeWebSocket(true, sent), TestSupport.newActivityLogger());
        session.setUser(new User(99, "carol", 1200));
        session.setCurrentRoomId("NO_SUCH_ROOM");

        handler.handle(session, MessageCodec.decodeEnvelope(
                MessageCodec.encode(MessageType.SELECT, new SelectPayload(true, 0, 0))));

        assertTrue(sent.get(0).contains("Game has not started yet"));
    }
}
