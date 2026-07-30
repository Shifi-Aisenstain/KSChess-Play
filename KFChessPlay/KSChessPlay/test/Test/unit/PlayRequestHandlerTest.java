package Test.unit;

import org.junit.jupiter.api.Test;
import server.auth.User;
import server.dispatch.handlers.PlayCancelHandler;
import server.dispatch.handlers.PlayRequestHandler;
import server.matchmaking.MatchRequest;
import server.matchmaking.MatchmakingService;
import server.net.PlayerSession;
import server.rating.EloRatingService;
import shared.eventbus.EventBus;
import shared.protocol.Message;
import shared.protocol.MessageCodec;
import shared.protocol.MessageType;
import shared.protocol.payload.EmptyPayload;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

public class PlayRequestHandlerTest {

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

    /** Recorded via {@code server.matchmaking.MatchmakerRuntime}, which wraps this in its own
     *  translation {@code MatchListener} before enqueueing - see {@code TestSupport}'s javadoc. So
     *  {@code lastRequest.listener} below is that translation listener, not literally the handler's
     *  own anonymous listener; invoking it still round-trips through the bus exactly like a real
     *  match result would, ending up back at the handler's listener via {@code MatchmakingClient}. */
    private static final class RecordingMatchmakingService implements MatchmakingService {
        MatchRequest lastRequest;
        boolean cancelled = false;
        long cancelledUserId = -1;
        @Override public void enqueue(MatchRequest request) { this.lastRequest = request; }
        @Override public void cancel(long userId) { cancelled = true; cancelledUserId = userId; }
        @Override public void shutdown() { }
    }

    private Message emptyMessage(MessageType type) {
        return MessageCodec.decodeEnvelope(MessageCodec.encode(type, EmptyPayload.INSTANCE));
    }

    private PlayerSession loggedInSession(User user, List<String> sent) throws Exception {
        PlayerSession session = new PlayerSession(TestSupport.fakeWebSocket(true, sent), TestSupport.newActivityLogger());
        session.setUser(user);
        return session;
    }

    private TestSupport.GatewayHarness harness(RecordingMatchmakingService matchmaking) {
        return TestSupport.newGatewayHarness(new EventBus(), new EloRatingService(), new InMemoryUserRepository(), matchmaking);
    }

    @Test
    public void testPlayRequestSendsWaitingAndEnqueues() throws Exception {
        RecordingMatchmakingService matchmaking = new RecordingMatchmakingService();
        TestSupport.GatewayHarness harness = harness(matchmaking);
        PlayRequestHandler handler = new PlayRequestHandler(harness.matchmakingClient, harness.roomRegistry);

        List<String> sent = new ArrayList<>();
        User alice = new User(1, "alice", 1200);
        handler.handle(loggedInSession(alice, sent), emptyMessage(MessageType.PLAY_REQUEST));

        assertEquals(1, sent.size());
        assertNotNull(matchmaking.lastRequest);
        assertEquals(alice.getId(), matchmaking.lastRequest.userId);
    }

    @Test
    public void testPlayRequestWithoutLoginIsRejected() throws Exception {
        RecordingMatchmakingService matchmaking = new RecordingMatchmakingService();
        TestSupport.GatewayHarness harness = harness(matchmaking);
        PlayRequestHandler handler = new PlayRequestHandler(harness.matchmakingClient, harness.roomRegistry);

        List<String> sent = new ArrayList<>();
        PlayerSession anon = new PlayerSession(TestSupport.fakeWebSocket(true, sent), TestSupport.newActivityLogger());
        handler.handle(anon, emptyMessage(MessageType.PLAY_REQUEST));

        assertNull(matchmaking.lastRequest);
        assertTrue(sent.get(0).contains("log in first"));
    }

    @Test
    public void testMatchFoundCallbackSendsMatchFoundAndAttachesSink() throws Exception {
        RecordingMatchmakingService matchmaking = new RecordingMatchmakingService();
        TestSupport.GatewayHarness harness = harness(matchmaking);
        PlayRequestHandler handler = new PlayRequestHandler(harness.matchmakingClient, harness.roomRegistry);

        List<String> sent = new ArrayList<>();
        User alice = new User(1, "alice", 1200);
        PlayerSession session = loggedInSession(alice, sent);
        handler.handle(session, emptyMessage(MessageType.PLAY_REQUEST));

        matchmaking.lastRequest.listener.onMatchFound("ROOM1", 'w', "bob", 1210);

        assertEquals("ROOM1", session.getCurrentRoomId());
        assertTrue(sent.get(sent.size() - 1).contains("bob"));
    }

    @Test
    public void testNotFoundCallbackSendsPlayNotFound() throws Exception {
        RecordingMatchmakingService matchmaking = new RecordingMatchmakingService();
        TestSupport.GatewayHarness harness = harness(matchmaking);
        PlayRequestHandler handler = new PlayRequestHandler(harness.matchmakingClient, harness.roomRegistry);

        List<String> sent = new ArrayList<>();
        User alice = new User(1, "alice", 1200);
        handler.handle(loggedInSession(alice, sent), emptyMessage(MessageType.PLAY_REQUEST));

        matchmaking.lastRequest.listener.onNotFound();

        assertTrue(sent.get(sent.size() - 1).contains("ELO range"));
    }

    @Test
    public void testPlayCancelDelegatesToMatchmakingService() throws Exception {
        RecordingMatchmakingService matchmaking = new RecordingMatchmakingService();
        TestSupport.GatewayHarness harness = harness(matchmaking);
        PlayCancelHandler handler = new PlayCancelHandler(harness.matchmakingClient);

        List<String> sent = new ArrayList<>();
        User alice = new User(1, "alice", 1200);
        handler.handle(loggedInSession(alice, sent), emptyMessage(MessageType.PLAY_CANCEL));

        assertTrue(matchmaking.cancelled);
        assertEquals(1, matchmaking.cancelledUserId);
    }

    @Test
    public void testPlayCancelWithoutLoginIsRejected() throws Exception {
        RecordingMatchmakingService matchmaking = new RecordingMatchmakingService();
        TestSupport.GatewayHarness harness = harness(matchmaking);
        PlayCancelHandler handler = new PlayCancelHandler(harness.matchmakingClient);

        List<String> sent = new ArrayList<>();
        PlayerSession anon = new PlayerSession(TestSupport.fakeWebSocket(true, sent), TestSupport.newActivityLogger());
        handler.handle(anon, emptyMessage(MessageType.PLAY_CANCEL));

        assertFalse(matchmaking.cancelled);
    }
}
