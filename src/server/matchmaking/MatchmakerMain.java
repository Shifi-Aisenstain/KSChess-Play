package server.matchmaking;

import server.gateway.RoomLifecycleClient;
import server.logging.ServerActivityLogger;
import shared.bus.MessageBus;
import shared.bus.NatsMessageBus;
import shared.eventbus.EventBus;
import shared.health.HealthServer;
import shared.metrics.MetricsRegistry;
import shared.redis.RedisConnectionProvider;

public final class MatchmakerMain {
    private static final int ELO_MATCH_RANGE = 100;
    private static final String DEFAULT_NATS_URL = "nats://nats:4222";
    private static final String DEFAULT_REDIS_URL = "redis://redis:6379";
    private static final String ACTIVITY_LOG_FILE = "logs/matchmaker/activity.log";
    private static final int DEFAULT_HEALTH_PORT = 8081;

    public static void main(String[] args) {
        String natsUrl = System.getenv().getOrDefault("NATS_URL", DEFAULT_NATS_URL);
        String redisUrl = System.getenv().getOrDefault("REDIS_URL", DEFAULT_REDIS_URL);
        int healthPort = Integer.parseInt(System.getenv().getOrDefault("HEALTH_PORT", String.valueOf(DEFAULT_HEALTH_PORT)));

        EventBus eventBus = new EventBus();
        ServerActivityLogger activityLogger = new ServerActivityLogger(ACTIVITY_LOG_FILE, eventBus);

        MessageBus bus = NatsMessageBus.connect(natsUrl);
        Runtime.getRuntime().addShutdownHook(new Thread(bus::close));

        RedisConnectionProvider redis = new RedisConnectionProvider(redisUrl);
        Runtime.getRuntime().addShutdownHook(new Thread(redis::close));

        RoomLifecycleClient roomLifecycle = new RoomLifecycleClient(bus);
        MatchmakingService matchmakingService = new MatchmakingServiceImpl(
                new EloRangeMatchCriteria(ELO_MATCH_RANGE), new BusRoomAllocationClient(roomLifecycle),
                new RedisWaitingPlayersQueue(redis));
        MetricsRegistry metricsRegistry = new MetricsRegistry();
        new MatchmakerRuntime(bus, matchmakingService, activityLogger, metricsRegistry).start();
        HealthServer.startOnPort(healthPort, metricsRegistry);

        System.out.println("[matchmaker] connected to " + natsUrl + " and " + redisUrl
                + ", waiting for matchmaking requests...");
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
