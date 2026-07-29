package Test.unit;

import org.junit.jupiter.api.Test;
import server.auth.User;
import server.dispatch.handlers.RoomCreateHandler;
import server.net.PlayerSession;
import server.rating.EloRatingService;
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

public class RoomCreateHandlerTest {

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

    private PlayerSession loggedInSession(User user, List<String> sent) throws Exception {
        PlayerSession session = new PlayerSession(TestSupport.fakeWebSocket(true, sent), TestSupport.newActivityLogger());
        session.setUser(user);
        return session;
    }

    private Message createMessage(String roomId) {
        return MessageCodec.decodeEnvelope(MessageCodec.encode(MessageType.ROOM_CREATE, new RoomActionPayload(roomId)));
    }

    @Test
    public void testCreateRoomSendsRoomCreatedAndSetsSessionRoomId() throws Exception {
        roomManager = TestSupport.newRoomManagerWithLocalShard(new EventBus(), new EloRatingService(), new InMemoryUserRepository());
        RoomCreateHandler handler = new RoomCreateHandler(roomManager);

        List<String> sent = new ArrayList<>();
        User alice = new User(1, "alice", 1200);
        PlayerSession session = loggedInSession(alice, sent);
        handler.handle(session, createMessage("MYROOM"));

        assertEquals("MYROOM", session.getCurrentRoomId());
        assertTrue(sent.get(0).contains("ROOM_CREATED") || sent.get(0).contains("MYROOM"));
        assertTrue(sent.get(0).contains("WHITE"));
    }

    @Test
    public void testDuplicateCustomIdSendsError() throws Exception {
        roomManager = TestSupport.newRoomManagerWithLocalShard(new EventBus(), new EloRatingService(), new InMemoryUserRepository());
        RoomCreateHandler handler = new RoomCreateHandler(roomManager);

        User alice = new User(1, "alice", 1200);
        User bob = new User(2, "bob", 1200);
        handler.handle(loggedInSession(alice, new ArrayList<>()), createMessage("TAKEN"));

        List<String> sentBob = new ArrayList<>();
        handler.handle(loggedInSession(bob, sentBob), createMessage("TAKEN"));

        assertTrue(sentBob.get(0).contains("already taken") || sentBob.get(0).contains("ERROR"));
    }

    @Test
    public void testCreateRoomWithoutLoginIsRejected() throws Exception {
        roomManager = TestSupport.newRoomManagerWithLocalShard(new EventBus(), new EloRatingService(), new InMemoryUserRepository());
        RoomCreateHandler handler = new RoomCreateHandler(roomManager);

        List<String> sent = new ArrayList<>();
        PlayerSession anon = new PlayerSession(TestSupport.fakeWebSocket(true, sent), TestSupport.newActivityLogger());
        handler.handle(anon, createMessage("ANYROOM"));

        assertNull(anon.getCurrentRoomId());
        assertTrue(sent.get(0).contains("log in first"));
    }
}
