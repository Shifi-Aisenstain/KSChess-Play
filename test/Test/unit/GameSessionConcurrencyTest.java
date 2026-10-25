package Test.unit;

import models.Position;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import server.auth.User;
import server.game.GameSession;
import server.game.GameUpdateSink;
import server.history.GameHistoryRecorder;
import server.net.MessageSink;
import server.rating.EloRatingService;
import shared.eventbus.EventBus;
import shared.protocol.MessageType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

public class GameSessionConcurrencyTest {

    private static final class CapturingSink implements MessageSink {
        private final AtomicInteger stateUpdateCount = new AtomicInteger();
        @Override public void send(MessageType type, Object payload) {
            if (type == MessageType.STATE_UPDATE) stateUpdateCount.incrementAndGet();
        }
        int count() { return stateUpdateCount.get(); }
    }

    private static final class TestUpdateSink implements GameUpdateSink {
        private final Map<Long, MessageSink> sinksByUserId = new ConcurrentHashMap<>();
        void attach(long userId, MessageSink sink) { sinksByUserId.put(userId, sink); }
        @Override public void sendTo(long userId, MessageType type, Object payload) {
            MessageSink sink = sinksByUserId.get(userId);
            if (sink != null) sink.send(type, payload);
        }
        @Override public void broadcastToAll(MessageType type, Object payload) {
            for (MessageSink sink : sinksByUserId.values()) sink.send(type, payload);
        }
    }

    private GameSession startedSession;
    private User alice;

    @AfterEach
    public void tearDown() {

        if (startedSession != null) {
            startedSession.handleResign(alice.getId());
        }
    }

    @Test
    public void concurrentMovesJumpsAndTicksDoNotCorruptGameState() throws Exception {
        alice = new User(1, "alice", 1200);
        User bob = new User(2, "bob", 1200);

        TestUpdateSink updateSink = new TestUpdateSink();
        GameSession session = new GameSession("TESTROOM", updateSink, alice, bob,
                new EventBus(), new EloRatingService(), (userId, newElo) -> { }, GameHistoryRecorder.NO_OP);
        startedSession = session;
        session.start();

        CapturingSink aliceSink = new CapturingSink();
        updateSink.attach(alice.getId(), aliceSink);

        AtomicReference<Throwable> failure = new AtomicReference<>();
        CountDownLatch startLatch = new CountDownLatch(1);
        long hammerMillis = 1000;

        List<Runnable> workers = new ArrayList<>();

        for (int col = 0; col < 8; col++) {
            workers.add(jumpHammer(session, alice.getId(), new Position(6, col), startLatch, hammerMillis, failure));
            workers.add(jumpHammer(session, bob.getId(), new Position(1, col), startLatch, hammerMillis, failure));
        }

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
