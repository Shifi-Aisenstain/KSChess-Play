package Test.unit;

import org.junit.jupiter.api.Test;
import server.auth.AuthService;
import server.auth.User;
import server.dispatch.handlers.LoginHandler;
import server.net.PlayerSession;
import server.rating.EloRatingService;
import server.rooms.Room;
import server.rooms.RoomManager;
import shared.eventbus.EventBus;
import shared.protocol.Message;
import shared.protocol.MessageCodec;
import shared.protocol.MessageType;
import shared.protocol.payload.LoginPayload;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

public class LoginHandlerTest {

    private static final class StubAuthService implements AuthService {
        AuthService.Result nextResult;
        @Override public Result login(String username, String password) { return nextResult; }
    }

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

    private Message loginMessage(String username, String password) {
        return MessageCodec.decodeEnvelope(MessageCodec.encode(MessageType.LOGIN, new LoginPayload(username, password)));
    }

    private PlayerSession session(List<String> sent) throws Exception {
        return new PlayerSession(TestSupport.fakeWebSocket(true, sent), TestSupport.newActivityLogger());
    }

    @Test
    public void testSuccessfulLoginSendsLoginResultAndSetsUser() throws Exception {
        StubAuthService auth = new StubAuthService();
        auth.nextResult = AuthService.Result.ok(new User(1, "alice", 1200));
        RoomManager roomManager = TestSupport.newRoomManagerWithLocalShard(new EventBus(), new EloRatingService(), new InMemoryUserRepository());
        LoginHandler handler = new LoginHandler(auth, roomManager);

        List<String> sent = new ArrayList<>();
        PlayerSession session = session(sent);
        handler.handle(session, loginMessage("alice", "pw"));

        assertNotNull(session.getUser());
        assertEquals("alice", session.getUser().getUsername());
        assertEquals(1, sent.size());
        // payloadJson is embedded as an escaped JSON *string* inside the envelope (see
        // MessageCodec/ARCHITECTURE.md §7), so raw substring-matching quotes/colons on `sent` would
        // need to account for that escaping - decoding properly avoids the trap entirely.
        var payload = MessageCodec.decodePayload(MessageCodec.decodeEnvelope(sent.get(0)),
                shared.protocol.payload.LoginResultPayload.class);
        assertTrue(payload.success);
        assertEquals("alice", payload.username);
    }

    @Test
    public void testFailedLoginDoesNotSetUser() throws Exception {
        StubAuthService auth = new StubAuthService();
        auth.nextResult = AuthService.Result.fail("bad password");
        RoomManager roomManager = TestSupport.newRoomManagerWithLocalShard(new EventBus(), new EloRatingService(), new InMemoryUserRepository());
        LoginHandler handler = new LoginHandler(auth, roomManager);

        List<String> sent = new ArrayList<>();
        PlayerSession session = session(sent);
        handler.handle(session, loginMessage("alice", "wrong"));

        assertNull(session.getUser());
        assertTrue(sent.get(0).contains("bad password"));
    }

    @Test
    public void testLoginReattachesToActiveGameAfterDisconnect() throws Exception {
        EventBus eventBus = new EventBus();
        RoomManager roomManager = TestSupport.newRoomManagerWithLocalShard(eventBus, new EloRatingService(), new InMemoryUserRepository());
        User alice = new User(10, "alice", 1200);
        User bob = new User(11, "bob", 1200);
        Room room = roomManager.createRoomForMatch(alice, bob);
        try {
            assertTrue(roomManager.getActiveRoomForUser(alice.getId()).isPresent());

            StubAuthService auth = new StubAuthService();
            auth.nextResult = AuthService.Result.ok(alice);
            LoginHandler handler = new LoginHandler(auth, roomManager);

            List<String> sent = new ArrayList<>();
            PlayerSession newSession = session(sent);
            handler.handle(newSession, loginMessage("alice", "pw"));

            assertEquals(room.getId(), newSession.getCurrentRoomId());
        } finally {
            room.getGameSession().handleResign(alice.getId());
        }
    }
}
