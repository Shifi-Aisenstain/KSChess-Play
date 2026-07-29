package Test.unit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import server.auth.User;
import server.game.DisconnectWatchdog;
import server.net.MessageSink;
import server.rating.EloRatingService;
import server.rooms.Room;
import server.rooms.RoomManager;
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

/** DisconnectWatchdog ticks a real ScheduledExecutorService every second (see production code) -
  * these tests use short real-time waits (~1.2s) rather than mocking time, since the countdown
  * itself has no injectable clock. The final "reaches zero -> auto-resign" branch would need a real
  * 20s wait via the public API and is intentionally not covered here to keep the suite fast; the
  * cancel-on-reconnect and already-over-game paths (the two behaviors the spec cares about) are. */
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

    private Room room;
    private User alice;
    private DisconnectWatchdog watchdog;

    @AfterEach
    public void tearDown() {
        if (watchdog != null) watchdog.cancel();
        if (room != null && room.getGameSession() != null && !room.getGameSession().isOver()) {
            room.getGameSession().handleResign(alice.getId());
        }
    }

    @Test
    public void testStartBroadcastsDecreasingCountdown() throws Exception {
        RoomManager roomManager = TestSupport.newRoomManagerWithLocalShard(new EventBus(), new EloRatingService(), new InMemoryUserRepository());
        alice = new User(1, "alice", 1200);
        User bob = new User(2, "bob", 1200);
        room = roomManager.createRoomForMatch(alice, bob);
        RecordingSink sink = new RecordingSink();
        room.attachSink(alice.getId(), sink);

        watchdog = new DisconnectWatchdog(room, room.getGameSession(), alice.getId(), 'w');
        watchdog.start();
        Thread.sleep(1200);
        watchdog.cancel();

        assertTrue(sink.received.size() >= 2, "expected at least 2 ticks within 1.2s");
        assertEquals('w', sink.received.get(0).disconnectedColor);
        assertTrue(sink.received.get(1).secondsRemaining < sink.received.get(0).secondsRemaining);
    }

    @Test
    public void testCancelStopsFurtherTicks() throws Exception {
        RoomManager roomManager = TestSupport.newRoomManagerWithLocalShard(new EventBus(), new EloRatingService(), new InMemoryUserRepository());
        alice = new User(1, "alice", 1200);
        User bob = new User(2, "bob", 1200);
        room = roomManager.createRoomForMatch(alice, bob);
        RecordingSink sink = new RecordingSink();
        room.attachSink(alice.getId(), sink);

        watchdog = new DisconnectWatchdog(room, room.getGameSession(), alice.getId(), 'w');
        watchdog.start();
        Thread.sleep(1200);
        watchdog.cancel();
        int countAfterCancel = sink.received.size();
        Thread.sleep(1200);

        assertEquals(countAfterCancel, sink.received.size(), "no further ticks should arrive after cancel()");
    }

    @Test
    public void testWatchdogOnAlreadyOverGameNeverBroadcasts() throws Exception {
        RoomManager roomManager = TestSupport.newRoomManagerWithLocalShard(new EventBus(), new EloRatingService(), new InMemoryUserRepository());
        alice = new User(1, "alice", 1200);
        User bob = new User(2, "bob", 1200);
        room = roomManager.createRoomForMatch(alice, bob);
        room.getGameSession().handleResign(alice.getId());
        assertTrue(room.getGameSession().isOver());

        RecordingSink sink = new RecordingSink();
        room.attachSink(alice.getId(), sink);

        watchdog = new DisconnectWatchdog(room, room.getGameSession(), alice.getId(), 'w');
        watchdog.start();
        Thread.sleep(1200);

        assertTrue(sink.received.isEmpty(), "watchdog should short-circuit once the game is already over");
    }
}
