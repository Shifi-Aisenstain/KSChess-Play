package Test.unit;

import org.junit.jupiter.api.Test;
import server.auth.InMemorySessionStore;
import server.auth.SessionStore;
import server.auth.User;
import server.dispatch.handlers.IdentifyHandler;
import server.net.PlayerSession;
import server.rating.EloRatingService;
import server.rooms.Room;
import shared.eventbus.EventBus;
import shared.metrics.MetricsRegistry;
import shared.protocol.Message;
import shared.protocol.MessageCodec;
import shared.protocol.MessageType;
import shared.protocol.payload.IdentifyPayload;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

public class IdentifyHandlerTest {

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

    private Message identifyMessage(String sessionToken) {
        return MessageCodec.decodeEnvelope(MessageCodec.encode(MessageType.IDENTIFY, new IdentifyPayload(sessionToken)));
    }

    private PlayerSession session(List<String> sent) throws Exception {
        return new PlayerSession(TestSupport.fakeWebSocket(true, sent), TestSupport.newActivityLogger());
    }

    @Test
    public void testIdentifySetsUserAndSendsLoginResult() throws Exception {
        TestSupport.GatewayHarness harness =
                TestSupport.newGatewayHarness(new EventBus(), new EloRatingService(), new InMemoryUserRepository());
        SessionStore sessionStore = new InMemorySessionStore();
        String token = sessionStore.createSession(1, "alice", 1200);
        IdentifyHandler handler = new IdentifyHandler(harness.roomLifecycle, harness.roomRegistry, sessionStore, new MetricsRegistry());

        List<String> sent = new ArrayList<>();
        PlayerSession session = session(sent);
        handler.handle(session, identifyMessage(token));

        assertNotNull(session.getUser());
        assertEquals("alice", session.getUser().getUsername());
        assertEquals(1, sent.size());

        var payload = MessageCodec.decodePayload(MessageCodec.decodeEnvelope(sent.get(0)),
                shared.protocol.payload.LoginResultPayload.class);
        assertTrue(payload.success);
        assertEquals("alice", payload.username);
        assertEquals(1200, payload.elo);
    }

    @Test
    public void testIdentifyWithUnknownTokenIsRejected() throws Exception {
        TestSupport.GatewayHarness harness =
                TestSupport.newGatewayHarness(new EventBus(), new EloRatingService(), new InMemoryUserRepository());
        IdentifyHandler handler = new IdentifyHandler(harness.roomLifecycle, harness.roomRegistry, new InMemorySessionStore(),
                new MetricsRegistry());

        List<String> sent = new ArrayList<>();
        PlayerSession session = session(sent);
        handler.handle(session, identifyMessage("not-a-real-token"));

        assertNull(session.getUser(), "an invalid session token must never establish an identity");
        assertEquals(1, sent.size());
        Message reply = MessageCodec.decodeEnvelope(sent.get(0));
        assertEquals(MessageType.ERROR, reply.getType());
    }

    @Test
    public void testIdentifyReattachesToActiveGameAfterDisconnect() throws Exception {
        EventBus eventBus = new EventBus();
        TestSupport.GatewayHarness harness =
                TestSupport.newGatewayHarness(eventBus, new EloRatingService(), new InMemoryUserRepository());
        User alice = new User(10, "alice", 1200);
        User bob = new User(11, "bob", 1200);
        Room room = harness.roomManager.createRoomForMatch(alice, bob);
        try {
            assertTrue(harness.roomManager.getActiveRoomForUser(alice.getId()).isPresent());

            SessionStore sessionStore = new InMemorySessionStore();
            String token = sessionStore.createSession(alice.getId(), "alice", 1200);
            IdentifyHandler handler = new IdentifyHandler(harness.roomLifecycle, harness.roomRegistry, sessionStore, new MetricsRegistry());

            List<String> sent = new ArrayList<>();
            PlayerSession newSession = session(sent);
            handler.handle(newSession, identifyMessage(token));

            assertEquals(room.getId(), newSession.getCurrentRoomId());
        } finally {
            harness.forceResign(room.getId(), alice.getId());
        }
    }
}
