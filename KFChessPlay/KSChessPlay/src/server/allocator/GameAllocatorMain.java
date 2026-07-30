package server.allocator;

import server.logging.ServerActivityLogger;
import server.rooms.RoomManager;
import shared.bus.MessageBus;
import shared.bus.NatsMessageBus;
import shared.eventbus.EventBus;
import shared.health.HealthServer;
import shared.redis.RedisConnectionProvider;

import java.util.Arrays;
import java.util.List;

/**
 * Composition root / entry point for the Game Allocator (PLAN_split_6_services.md §3.4) - the
 * process that owns room creation/joining/cancellation and the active-room/reconnect registry
 * ({@link RoomManager}, moved here unchanged from the monolith), plus picking which Game Server Shard
 * runs each newly-full room ({@link ShardSelector}, round-robin over {@code SHARD_IDS}). Mirrors
 * {@code server.shard.GameServerShardMain}/{@code server.gateway.WsGatewayMain}'s role as a
 * composition root, just for this process.
 *
 * <p>Unlike its siblings, this process needs no SQLite connection at all: {@link RoomManager} only
 * ever touched {@code RatingService}/{@code UserRepository} to satisfy dead constructor parameters
 * (removed as part of §3.4) - Elo/user data live entirely in the Shard (game results) and the API
 * Gateway (login) instead. It does need Redis, since Server_Design.md step 4: the active-room
 * registry lives behind {@link ActiveRoomRegistry} (see {@link RedisActiveRoomRegistry}'s javadoc).
 *
 * <p>Not unit tested directly for the same reason its siblings aren't (see pom.xml's jacoco
 * excludes) - it needs a live NATS server to run at all. Its actual logic lives in
 * {@link GameAllocatorRuntime}, which <i>is</i> unit tested against {@code shared.bus.InMemoryMessageBus}.
 */
public final class GameAllocatorMain {
    private static final String DEFAULT_NATS_URL = "nats://nats:4222";
    private static final String DEFAULT_REDIS_URL = "redis://redis:6379";
    private static final String DEFAULT_SHARD_IDS = "shard-1";
    private static final String ACTIVITY_LOG_FILE = "allocator_activity.log";
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
        new ServerActivityLogger(ACTIVITY_LOG_FILE, eventBus);

        MessageBus bus = NatsMessageBus.connect(natsUrl);
        Runtime.getRuntime().addShutdownHook(new Thread(bus::close));

        RedisConnectionProvider redis = new RedisConnectionProvider(redisUrl);
        Runtime.getRuntime().addShutdownHook(new Thread(redis::close));

        RoomManager roomManager = new RoomManager(eventBus, bus, new ShardSelector(shardIds),
                new RedisActiveRoomRegistry(redis));
        new GameAllocatorRuntime(bus, roomManager).start();
        HealthServer.startOnPort(healthPort);

        System.out.println("[allocator] connected to " + natsUrl + " and " + redisUrl + ", shards=" + shardIds
                + ", answering room requests over the bus...");
        blockForever();
    }

    /** Same reasoning as {@code server.shard.GameServerShardMain}'s twin method. */
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
