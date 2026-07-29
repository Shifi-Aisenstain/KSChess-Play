package Test.unit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import server.auth.User;
import server.dispatch.handlers.JumpHandler;
import server.net.PlayerSession;
import server.rating.EloRatingService;
import server.rooms.Room;
import server.rooms.RoomManager;
import shared.eventbus.EventBus;
import shared.protocol.Message;
import shared.protocol.MessageCodec;
import shared.protocol.MessageType;
import shared.protocol.payload.JumpCommandPayload;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

public class JumpHandlerTest {

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
        roomManager = TestSupport.newRoomManagerWithLocalShard(new EventBus(), new EloRatingService(), new InMemoryUserRepository());
        alice = new User(1, "alice", 1200);
        bob = new User(2, "bob", 1200);
        room = roomManager.createRoomForMatch(alice, bob);
    }

    @AfterEach
    public void tearDown() {
        if (room != null && room.getGameSession() != null) {
            room.getGameSession().handleResign(alice.getId());
        }
    }

    private Message jumpMessage(int row, int col) {
        return MessageCodec.decodeEnvelope(MessageCodec.encode(MessageType.JUMP, new JumpCommandPayload(row, col)));
    }

    @Test
    public void testOwnPieceJumpIsAcceptedNoError() throws Exception {
        startGame();
        JumpHandler handler = new JumpHandler(roomManager);
        List<String> sent = new ArrayList<>();

        handler.handle(session(alice, sent), jumpMessage(6, 4));

        assertTrue(sent.isEmpty());
    }

    @Test
    public void testJumpingOpponentsPieceIsRejected() throws Exception {
        startGame();
        JumpHandler handler = new JumpHandler(roomManager);
        List<String> sent = new ArrayList<>();

        handler.handle(session(alice, sent), jumpMessage(1, 4));

        assertEquals(1, sent.size());
        assertTrue(sent.get(0).contains("Jump rejected"));
    }

    @Test
    public void testJumpFromEmptySquareIsRejected() throws Exception {
        startGame();
        JumpHandler handler = new JumpHandler(roomManager);
        List<String> sent = new ArrayList<>();

        handler.handle(session(alice, sent), jumpMessage(3, 3));

        assertTrue(sent.get(0).contains("Jump rejected"));
    }
}
