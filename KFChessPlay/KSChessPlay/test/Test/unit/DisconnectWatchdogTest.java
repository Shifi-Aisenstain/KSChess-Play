package Test.unit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import server.auth.User;
import server.gateway.DisconnectWatchdog;
import server.gateway.GatewayRoomSession;
import server.net.MessageSink;
import server.rating.EloRatingService;
import shared.eventbus.EventBus;
import shared.protocol.MessageType;
import shared.protocol.payload.CountdownPayload;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

public class DisconnectWatchdogTest {

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

    private static final class RecordingSink implements MessageSink {
        final List<CountdownPayload> received = new ArrayList<>();
        @Override public void send(MessageType type, Object payload) {
            if (payload instanceof CountdownPayload) received.add((CountdownPayload) payload);
        }
    }

    private TestSupport.GatewayHarness harness;
    private String roomId;
    private User alice;
    private DisconnectWatchdog watchdog;

    @AfterEach
    public void tearDown() {
        if (watchdog != null) watchdog.cancel();
        if (roomId != null) harness.forceResign(roomId, alice.getId());
    }

    private GatewayRoomSession startGameAndAttach(RecordingSink sink) {
        harness = TestSupport.newGatewayHarness(new EventBus(), new EloRatingService(), new InMemoryUserRepository());
        alice = new User(1, "alice", 1200);
        User bob = new User(2, "bob", 1200);
        var room = harness.roomManager.createRoomForMatch(alice, bob);
        roomId = room.getId();
        GatewayRoomSession session = harness.roomRegistry.get(roomId).orElseThrow();
        session.addLocalMember(alice.getId(), sink);
        return session;
    }

    @Test
    public void testStartBroadcastsDecreasingCountdown() throws Exception {
        RecordingSink sink = new RecordingSink();
        GatewayRoomSession session = startGameAndAttach(sink);

        watchdog = new DisconnectWatchdog(session, alice.getId(), 'w');
        watchdog.start();
        Thread.sleep(1200);
        watchdog.cancel();

        assertTrue(sink.received.size() >= 2, "expected at least 2 ticks within 1.2s");
        assertEquals('w', sink.received.get(0).disconnectedColor);
        assertTrue(sink.received.get(1).secondsRemaining < sink.received.get(0).secondsRemaining);
    }

    @Test
    public void testCancelStopsFurtherTicks() throws Exception {
        RecordingSink sink = new RecordingSink();
        GatewayRoomSession session = startGameAndAttach(sink);

        watchdog = new DisconnectWatchdog(session, alice.getId(), 'w');
        watchdog.start();
        Thread.sleep(1200);
        watchdog.cancel();
        int countAfterCancel = sink.received.size();
        Thread.sleep(1200);

        assertEquals(countAfterCancel, sink.received.size(), "no further ticks should arrive after cancel()");
    }

    @Test
    public void testWatchdogOnAlreadyOverGameNeverBroadcasts() throws Exception {
        RecordingSink sink = new RecordingSink();
        GatewayRoomSession session = startGameAndAttach(sink);
        session.forceResignDueToDisconnect(alice.getId());
        assertTrue(session.isOver());

        watchdog = new DisconnectWatchdog(session, alice.getId(), 'w');
        watchdog.start();
        Thread.sleep(1200);

        assertTrue(sink.received.isEmpty(), "watchdog should short-circuit once the game is already over");
    }
}
