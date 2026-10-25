package Test.unit;

import org.junit.jupiter.api.Test;
import server.auth.User;
import server.dispatch.handlers.PingHandler;
import server.dispatch.handlers.ResignHandler;
import server.net.PlayerSession;
import server.rating.EloRatingService;
import server.rooms.Room;
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

public class ResignHandlerTest {

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

    private PlayerSession session(User user, String roomId, List<String> sent) throws Exception {
        PlayerSession s = new PlayerSession(TestSupport.fakeWebSocket(true, sent), TestSupport.newActivityLogger());
        s.setUser(user);
        s.setCurrentRoomId(roomId);
        return s;
    }

    private Message emptyMessage(MessageType type) {
        return MessageCodec.decodeEnvelope(MessageCodec.encode(type, EmptyPayload.INSTANCE));
    }

    @Test
    public void testResignEndsTheGame() throws Exception {
        TestSupport.GatewayHarness harness =
                TestSupport.newGatewayHarness(new EventBus(), new EloRatingService(), new InMemoryUserRepository());
        User alice = new User(1, "alice", 1200);
        User bob = new User(2, "bob", 1200);
        Room room = harness.roomManager.createRoomForMatch(alice, bob);

        ResignHandler handler = new ResignHandler(harness.roomRegistry);
        handler.handle(session(alice, room.getId(), new ArrayList<>()), emptyMessage(MessageType.RESIGN));

        assertTrue(harness.roomRegistry.get(room.getId()).orElseThrow().isOver());
    }

    @Test
    public void testResignWithoutActiveGameSendsError() throws Exception {
        TestSupport.GatewayHarness harness =
                TestSupport.newGatewayHarness(new EventBus(), new EloRatingService(), new InMemoryUserRepository());
        ResignHandler handler = new ResignHandler(harness.roomRegistry);

        List<String> sent = new ArrayList<>();
        User carol = new User(99, "carol", 1200);
        handler.handle(session(carol, null, sent), emptyMessage(MessageType.RESIGN));

        assertTrue(sent.get(0).contains("not in a room"));
    }

    @Test
    public void testPingRepliesWithPong() throws Exception {
        PingHandler handler = new PingHandler();
        List<String> sent = new ArrayList<>();
        User alice = new User(1, "alice", 1200);
        handler.handle(session(alice, null, sent), emptyMessage(MessageType.PING));

        assertEquals(1, sent.size());
        assertTrue(sent.get(0).contains("PONG"));
    }
}
