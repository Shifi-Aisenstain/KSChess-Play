package server.persistence;

import server.auth.UserRepository;
import shared.bus.MessageBus;
import shared.bus.RoomBusSubjects;
import shared.protocol.MessageCodec;
import shared.protocol.payload.EloUpdateRequestedPayload;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * The actual "batch and drain" logic of the Elo writer (Server_Design.md step 5), factored out of
 * {@link EloWriterMain} so it can be exercised directly against a {@code shared.bus.InMemoryMessageBus}
 * and a fake {@link UserRepository} in tests - the same split as every other {@code *Runtime}/
 * {@code *Main} pair in this codebase (e.g. {@code server.shard.GameShardRuntime}).
 *
 * <p>Subscribes to {@link RoomBusSubjects#ELO_UPDATES} and buffers every incoming
 * {@link EloUpdateRequestedPayload} in a lock-free queue (cheap, since the publishing side - a Game
 * Server Shard's {@link BusEloWriter} - never waits for this to do anything); every
 * {@link #FLUSH_INTERVAL_MS} it drains that queue and writes the whole batch through
 * {@link UserRepository#updateEloBatch} in one go, rather than one write per game-ended event. If two
 * updates for the same userId land in the same flush window (a game ending is a one-time event per
 * user per game, so this should be rare in practice), the later one - not the earlier one - wins,
 * since {@link LinkedHashMap#put} overwrites in insertion (i.e. arrival) order.
 */
public final class EloWriterRuntime {
    private static final long DEFAULT_FLUSH_INTERVAL_MS = 2000;

    private final MessageBus bus;
    private final UserRepository userRepository;
    private final long flushIntervalMs;
    private final ConcurrentLinkedQueue<EloUpdateRequestedPayload> pending = new ConcurrentLinkedQueue<>();
    private final ScheduledExecutorService flushScheduler = Executors.newSingleThreadScheduledExecutor(
            r -> new Thread(r, "elo-writer-flush"));

    public EloWriterRuntime(MessageBus bus, UserRepository userRepository) {
        this(bus, userRepository, DEFAULT_FLUSH_INTERVAL_MS);
    }

    /** @param flushIntervalMs how often to drain the buffer - shorter in tests (see
     *                         {@code EloWriterRuntimeTest}) so they don't need to wait out a real
     *                         production-scale interval. */
    public EloWriterRuntime(MessageBus bus, UserRepository userRepository, long flushIntervalMs) {
        this.bus = bus;
        this.userRepository = userRepository;
        this.flushIntervalMs = flushIntervalMs;
    }

    public void start() {
        bus.subscribe(RoomBusSubjects.ELO_UPDATES, (message, replyTo) ->
                pending.add(MessageCodec.decodePayload(message, EloUpdateRequestedPayload.class)));
        flushScheduler.scheduleWithFixedDelay(this::flush, flushIntervalMs, flushIntervalMs, TimeUnit.MILLISECONDS);
    }

    private void flush() {
        Map<Long, Integer> latestByUserId = new LinkedHashMap<>();
        EloUpdateRequestedPayload update;
        while ((update = pending.poll()) != null) {
            latestByUserId.put(update.userId, update.newElo);
        }
        if (latestByUserId.isEmpty()) return;

        try {
            userRepository.updateEloBatch(latestByUserId);
        } catch (RuntimeException e) {
            System.err.println("[elo-writer] batch flush failed (" + latestByUserId.size()
                    + " updates lost): " + e.getMessage());
        }
    }
}
