package server.persistence;

import server.auth.UserRepository;
import shared.bus.MessageBus;
import shared.bus.RoomBusSubjects;
import shared.metrics.MetricsRegistry;
import shared.protocol.MessageCodec;
import shared.protocol.payload.EloUpdateRequestedPayload;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class EloWriterRuntime {
    private static final long DEFAULT_FLUSH_INTERVAL_MS = 2000;

    private final MessageBus bus;
    private final UserRepository userRepository;
    private final long flushIntervalMs;
    private final MetricsRegistry metricsRegistry;
    private final ConcurrentLinkedQueue<EloUpdateRequestedPayload> pending = new ConcurrentLinkedQueue<>();
    private final ScheduledExecutorService flushScheduler = Executors.newSingleThreadScheduledExecutor(
            r -> new Thread(r, "elo-writer-flush"));

    public EloWriterRuntime(MessageBus bus, UserRepository userRepository) {
        this(bus, userRepository, DEFAULT_FLUSH_INTERVAL_MS, new MetricsRegistry());
    }

    public EloWriterRuntime(MessageBus bus, UserRepository userRepository, long flushIntervalMs) {
        this(bus, userRepository, flushIntervalMs, new MetricsRegistry());
    }

    public EloWriterRuntime(MessageBus bus, UserRepository userRepository, long flushIntervalMs,
                             MetricsRegistry metricsRegistry) {
        this.bus = bus;
        this.userRepository = userRepository;
        this.flushIntervalMs = flushIntervalMs;
        this.metricsRegistry = metricsRegistry;
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
            metricsRegistry.incrementCounterBy("elowriter_elo_updates_written_total",
                    "Total Elo updates flushed to storage", latestByUserId.size());
        } catch (RuntimeException e) {
            System.err.println("[elo-writer] batch flush failed (" + latestByUserId.size()
                    + " updates lost): " + e.getMessage());
            metricsRegistry.incrementCounter("elowriter_flush_failures_total", "Total failed Elo batch flushes");
        }
    }
}
