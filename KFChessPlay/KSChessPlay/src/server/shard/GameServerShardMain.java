package server.shard;

import server.history.GameHistoryRecorder;
import server.logging.ServerActivityLogger;
import server.persistence.BusEloWriter;
import server.persistence.BusGameHistoryRecorder;
import server.rating.EloRatingService;
import server.rating.EloWriter;
import server.rating.RatingService;
import shared.bus.MessageBus;
import shared.bus.NatsMessageBus;
import shared.eventbus.EventBus;
import shared.health.HealthServer;
import shared.metrics.MetricsRegistry;

public final class GameServerShardMain {
    private static final String DEFAULT_NATS_URL = "nats://nats:4222";
    private static final String DEFAULT_SHARD_ID = "shard-1";
    private static final String ACTIVITY_LOG_FILE = "logs/shard/activity.log";
    private static final int DEFAULT_HEALTH_PORT = 8081;

    public static void main(String[] args) {
        String natsUrl = args.length > 0 ? args[0]
                : System.getenv().getOrDefault("NATS_URL", DEFAULT_NATS_URL);

        String shardId = System.getenv().getOrDefault("SHARD_ID", DEFAULT_SHARD_ID);
        int healthPort = Integer.parseInt(System.getenv().getOrDefault("HEALTH_PORT", String.valueOf(DEFAULT_HEALTH_PORT)));

        EventBus eventBus = new EventBus();
        new ServerActivityLogger(ACTIVITY_LOG_FILE, eventBus);
        RatingService ratingService = new EloRatingService();

        MessageBus bus = NatsMessageBus.connect(natsUrl);
        EloWriter eloWriter = new BusEloWriter(bus);
        GameHistoryRecorder historyRecorder = new BusGameHistoryRecorder(bus);
        MetricsRegistry metricsRegistry = new MetricsRegistry();
        GameShardRuntime runtime = new GameShardRuntime(bus, eventBus, ratingService, eloWriter, historyRecorder, shardId, metricsRegistry);
        runtime.start();
        HealthServer.startOnPort(healthPort, metricsRegistry);

        System.out.println("[shard " + shardId + "] connected to " + natsUrl + ", waiting for room.allocated...");
        Runtime.getRuntime().addShutdownHook(new Thread(bus::close));
        blockForever();
    }

    private static void blockForever() {
        Object lock = new Object();
        synchronized (lock) {
            try {
                lock.wait();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
