package Test.unit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import server.auth.User;
import server.dispatch.handlers.MoveHandler;
import server.net.PlayerSession;
import server.rating.EloRatingService;
import server.rooms.Room;
import shared.eventbus.EventBus;
import shared.protocol.Message;
import shared.protocol.MessageCodec;
import shared.protocol.MessageType;
import shared.protocol.payload.MoveCommandPayload;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

public class MoveHandlerTest {

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

    private Message moveMessage(int fromRow, int fromCol, int toRow, int toCol) {
        return MessageCodec.decodeEnvelope(
                MessageCodec.encode(MessageType.MOVE, new MoveCommandPayload(fromRow, fromCol, toRow, toCol)));
    }

    @Test
    public void testOwnPieceMoveIsAcceptedNoError() throws Exception {
        startGame();
        MoveHandler handler = new MoveHandler(harness.roomRegistry);
        List<String> sent = new ArrayList<>();

        handler.handle(session(alice, sent), moveMessage(6, 4, 4, 4));

        assertTrue(sent.isEmpty(), "no ERROR should be sent for a legal move by its owner");
    }

    @Test
    public void testMovingOpponentsPieceIsRejected() throws Exception {
        startGame();
        MoveHandler handler = new MoveHandler(harness.roomRegistry);
        List<String> sent = new ArrayList<>();

        // alice is WHITE; trying to move a BLACK pawn should be rejected as "not your piece".
        handler.handle(session(alice, sent), moveMessage(1, 4, 3, 4));

        assertEquals(1, sent.size());
        assertTrue(sent.get(0).contains("Move rejected"));
    }

    @Test
    public void testMoveWithoutActiveGameSendsError() throws Exception {
        startGame();
        MoveHandler handler = new MoveHandler(harness.roomRegistry);
        List<String> sent = new ArrayList<>();
        PlayerSession noRoomSession = new PlayerSession(TestSupport.fakeWebSocket(true, sent), TestSupport.newActivityLogger());
        noRoomSession.setUser(new User(99, "carol", 1200));

        handler.handle(noRoomSession, moveMessage(6, 4, 4, 4));

        assertTrue(sent.get(0).contains("not in a room"));
    }
}
