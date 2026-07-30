package server.allocator;

import server.logging.ServerActivityLogger;
import server.rooms.RoomManager;
import shared.bus.MessageBus;
import shared.bus.NatsMessageBus;
import shared.eventbus.EventBus;
import shared.health.HealthServer;
import shared.metrics.MetricsRegistry;
import shared.redis.RedisConnectionProvider;

import java.util.Arrays;
import java.util.List;

public final class GameAllocatorMain {
    private static final String DEFAULT_NATS_URL = "nats://nats:4222";
    private static final String DEFAULT_REDIS_URL = "redis://redis:6379";
    private static final String DEFAULT_SHARD_IDS = "shard-1";
    private static final String ACTIVITY_LOG_FILE = "logs/allocator/activity.log";
    private static final int DEFAULT_HEALTH_PORT = 8081;

    public static void main(String[] args) {
        String natsUrl = System.getenv().getOrDefault("NATS_URL", DEFAULT_NATS_URL);
        String redisUrl = System.getenv().getOrDefault("REDIS_URL", DEFAULT_REDIS_URL);
        List<String> shardIds = Arrays.stream(System.getenv().getOrDefault("SHARD_IDS", DEFAULT_SHARD_IDS).split(","))
                .map(String::trim)
                .filter(id -> !id.isEmpty())
                .toList();
        int healthPort = Integer.parseInt(System.getenv().getOrDefault("HEALTH_PORT", String.valueOf(DEFAULT_HEALTH_PORT)));

        EventBus eventBus = new EventBus();
        ServerActivityLogger activityLogger = new ServerActivityLogger(ACTIVITY_LOG_FILE, eventBus);

        MessageBus bus = NatsMessageBus.connect(natsUrl);
        Runtime.getRuntime().addShutdownHook(new Thread(bus::close));

        RedisConnectionProvider redis = new RedisConnectionProvider(redisUrl);
        Runtime.getRuntime().addShutdownHook(new Thread(redis::close));

        RoomManager roomManager = new RoomManager(eventBus, bus, new ShardSelector(shardIds),
                new RedisActiveRoomRegistry(redis));
        MetricsRegistry metricsRegistry = new MetricsRegistry();
        new GameAllocatorRuntime(bus, roomManager, activityLogger, metricsRegistry).start();
        HealthServer.startOnPort(healthPort, metricsRegistry);

        System.out.println("[allocator] connected to " + natsUrl + " and " + redisUrl + ", shards=" + shardIds
                + ", answering room requests over the bus...");
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
