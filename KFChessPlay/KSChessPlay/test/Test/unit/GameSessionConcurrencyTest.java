package Test.unit;

import models.Position;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import server.auth.User;
import server.auth.UserRepository;
import server.game.GameSession;
import server.net.MessageSink;
import server.rating.EloRatingService;
import server.rooms.Room;
import server.rooms.RoomUpdateSink;
import shared.eventbus.EventBus;
import shared.protocol.MessageType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression test for the race condition flagged in code review: {@code GameSession.handleMove}/
 * {@code handleJump} are called from whichever WebSocket connection thread received the message
 * (both players' sockets can call in concurrently), while {@code GameSession}'s own ticker thread
 * independently calls {@code tick()} every 50ms. Both paths read/write the same
 * {@code GameManager} / {@code RealTimeArbiter} / {@code Board}, none of which are thread-safe on
 * their own - before the fix (a {@code gameStateLock} guarding every access from GameSession),
 * hammering moves/jumps from several threads while the real ticker ran could throw
 * {@code ConcurrentModificationException} out of {@code RealTimeArbiter.updateEvents()}, or - more
 * insidiously - let that exception escape {@code tick()} itself, which silently and permanently
 * stops all future ticks ({@link java.util.concurrent.ScheduledExecutorService#scheduleAtFixedRate}
 * suppresses further executions after an uncaught exception) without the calling threads ever
 * seeing an error.
 *
 * <p>This drives that exact scenario with real threads and a real (unmocked) ticker for about a
 * second of wall-clock time - long enough to span several jump/cooldown cycles - then checks both
 * failure modes: no exception escapes the direct calls, and the ticker is still alive and
 * broadcasting afterwards (proven by a capturing sink continuing to receive STATE_UPDATEs).
 *
 * <p>Constructs the real {@link GameSession} directly against a {@link RoomUpdateSink} wrapping a
 * plain {@link Room}, rather than going through {@code RoomManager} - since PLAN_split_6_services.md
 * §3.1, {@code RoomManager.startGameSession} hands rooms off to a {@code RemoteGameSessionHandle} /
 * external Shard instead of running the engine in-process, which would defeat the entire point of
 * this test (it wants the real engine, real ticker, and real lock contention in *this* JVM).
 */
public class GameSessionConcurrencyTest {

    private static final class CapturingSink implements MessageSink {
        private final AtomicInteger stateUpdateCount = new AtomicInteger();
        @Override public void send(MessageType type, Object payload) {
            if (type == MessageType.STATE_UPDATE) stateUpdateCount.incrementAndGet();
        }
        int count() { return stateUpdateCount.get(); }
    }

    private static final class InMemoryUserRepository implements UserRepository {
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

    private GameSession startedSession;
    private User alice;

    @AfterEach
    public void tearDown() {
        // Each started GameSession spins up a background ticking thread - resign it so a failed
        // assertion above doesn't leak a running ScheduledExecutorService into later tests.
        if (startedSession != null) {
            startedSession.handleResign(alice.getId());
        }
    }

    @Test
    public void concurrentMovesJumpsAndTicksDoNotCorruptGameState() throws Exception {
        alice = new User(1, "alice", 1200);
        User bob = new User(2, "bob", 1200);
        Room room = new Room("TESTROOM");
        room.reserveRole(alice); // WHITE (first reservation)
        room.reserveRole(bob);   // BLACK (second reservation)

        GameSession session = new GameSession("TESTROOM", new RoomUpdateSink(room), alice, bob,
                new EventBus(), new EloRatingService(), new InMemoryUserRepository());
        room.setGameSession(session);
        startedSession = session;
        session.start();

        CapturingSink aliceSink = new CapturingSink();
        room.attachSink(alice.getId(), aliceSink);

        AtomicReference<Throwable> failure = new AtomicReference<>();
        CountDownLatch startLatch = new CountDownLatch(1);
        long hammerMillis = 1000;

        List<Runnable> workers = new ArrayList<>();
        // One thread per white pawn (row 6) and one per black pawn (row 1), all repeatedly
        // jumping their own square. Jumps never relocate a piece (JumpEvent just re-places it at
        // the same square), so these are real, accepted mutations of RealTimeArbiter's active
        // event list - spread across 16 distinct squares and both players - without ever risking
        // a captured king or otherwise changing board layout.
        for (int col = 0; col < 8; col++) {
            workers.add(jumpHammer(session, alice.getId(), new Position(6, col), startLatch, hammerMillis, failure));
            workers.add(jumpHammer(session, bob.getId(), new Position(1, col), startLatch, hammerMillis, failure));
        }
        // A few threads throwing essentially-random move attempts at the same session - mostly
        // rejected by ownership/legality checks, occasionally a real single-step move - adding
        // extra contention on the lock alongside the jump threads and the real ticker.
        for (int i = 0; i < 2; i++) {
            workers.add(randomMoveHammer(session, alice.getId(), startLatch, hammerMillis, failure));
            workers.add(randomMoveHammer(session, bob.getId(), startLatch, hammerMillis, failure));
        }

        ExecutorService pool = Executors.newFixedThreadPool(workers.size());
        List<Future<?>> futures = new ArrayList<>();
        for (Runnable worker : workers) {
            futures.add(pool.submit(worker));
        }

        startLatch.countDown();
        for (Future<?> f : futures) {
            f.get(hammerMillis + 5000, TimeUnit.MILLISECONDS);
        }
        pool.shutdown();

        int updatesDuringStorm = aliceSink.count();

        // Give the ticker a bit more real time so we can observe whether it's still alive: if an
        // exception ever escaped tick() during the storm, the ScheduledExecutorService would have
        // silently stopped scheduling further runs and no new STATE_UPDATEs would arrive here.
        Thread.sleep(300);

        assertNull(failure.get(), "no exception should escape concurrent move/jump handling: " + failure.get());
        assertFalse(session.isOver(), "pawns jumping in place / single-step shuffling shouldn't end the game");
        assertEquals(8, session.getBoardRows(), "board should still be intact and readable after the storm");
        assertTrue(aliceSink.count() > updatesDuringStorm,
                "ticker should still be broadcasting after the concurrency storm, not silently dead");
    }

    private Runnable jumpHammer(GameSession session, long userId, Position at, CountDownLatch startLatch,
                                 long durationMillis, AtomicReference<Throwable> failure) {
        return () -> {
            try {
                startLatch.await();
                long deadline = System.currentTimeMillis() + durationMillis;
                while (System.currentTimeMillis() < deadline && failure.get() == null) {
                    session.handleJump(userId, at);
                    Thread.sleep(2);
                }
            } catch (Throwable t) {
                failure.compareAndSet(null, t);
            }
        };
    }

    private Runnable randomMoveHammer(GameSession session, long userId, CountDownLatch startLatch,
                                       long durationMillis, AtomicReference<Throwable> failure) {
        return () -> {
            try {
                startLatch.await();
                Random random = new Random();
                long deadline = System.currentTimeMillis() + durationMillis;
                while (System.currentTimeMillis() < deadline && failure.get() == null) {
                    Position from = new Position(random.nextInt(8), random.nextInt(8));
                    Position to = new Position(random.nextInt(8), random.nextInt(8));
                    session.handleMove(userId, from, to);
                    Thread.sleep(2);
                }
            } catch (Throwable t) {
                failure.compareAndSet(null, t);
            }
        };
    }
}
