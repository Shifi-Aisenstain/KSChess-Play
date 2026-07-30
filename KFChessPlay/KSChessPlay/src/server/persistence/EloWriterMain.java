package server.persistence;

import server.config.PersistenceConfig;
import server.logging.ServerActivityLogger;
import shared.bus.MessageBus;
import shared.bus.NatsMessageBus;
import shared.eventbus.EventBus;
import shared.health.HealthServer;
import shared.metrics.MetricsRegistry;

public final class EloWriterMain {
    private static final String DEFAULT_NATS_URL = "nats://nats:4222";
    private static final String ACTIVITY_LOG_FILE = "logs/elowriter/activity.log";
    private static final int DEFAULT_HEALTH_PORT = 8081;

    public static void main(String[] args) {
        String natsUrl = System.getenv().getOrDefault("NATS_URL", DEFAULT_NATS_URL);
        int healthPort = Integer.parseInt(System.getenv().getOrDefault("HEALTH_PORT", String.valueOf(DEFAULT_HEALTH_PORT)));

        EventBus eventBus = new EventBus();
        ServerActivityLogger activityLogger = new ServerActivityLogger(ACTIVITY_LOG_FILE, eventBus);
        PersistenceConfig.PersistenceBundle persistence = PersistenceConfig.initialize(activityLogger);

        MessageBus bus = NatsMessageBus.connect(natsUrl);
        Runtime.getRuntime().addShutdownHook(new Thread(bus::close));

        MetricsRegistry metricsRegistry = new MetricsRegistry();
        new EloWriterRuntime(bus, persistence.users, 2000, metricsRegistry).start();
        new GameHistoryWriterRuntime(bus, persistence.history, metricsRegistry).start();
        HealthServer.startOnPort(healthPort, metricsRegistry);

        System.out.println("[elowriter] connected to " + natsUrl + ", batching Elo updates and game history into storage...");
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
