package server.matchmaking;

import server.gateway.RoomLifecycleClient;
import shared.bus.MessageBus;
import shared.bus.NatsMessageBus;
import shared.health.HealthServer;
import shared.redis.RedisConnectionProvider;

/**
 * Composition root / entry point for the Matchmaker (PLAN_split_6_services.md §3.3) - the process
 * that runs the ELO-range pairing queue ({@link MatchmakingServiceImpl}, moved here unchanged from
 * the monolith), reaching the Game Allocator only over the bus (via {@link BusRoomAllocationClient})
 * instead of calling {@code RoomManager.createRoomForMatch} in-process. Mirrors
 * {@code server.shard.GameServerShardMain}/{@code server.allocator.GameAllocatorMain}'s role as a
 * composition root, just for this process.
 *
 * <p>Needs no SQLite connection or {@code ServerActivityLogger}: matchmaking has no domain events of
 * its own, and every user-facing outcome already flows back over {@code matchmaking.results}. Since
 * Server_Design.md step 4, it does need Redis: the waiting-players queue itself lives behind
 * {@link WaitingPlayersQueue} (see {@link RedisWaitingPlayersQueue}'s javadoc for exactly what is and
 * isn't shared this way).
 *
 * <p>Not unit tested directly for the same reason its siblings aren't (see pom.xml's jacoco
 * excludes) - it needs a live NATS server to run at all. Its actual logic lives in
 * {@link MatchmakerRuntime}, which <i>is</i> unit tested against {@code shared.bus.InMemoryMessageBus}.
 */
public final class MatchmakerMain {
    private static final int ELO_MATCH_RANGE = 100;
    private static final String DEFAULT_NATS_URL = "nats://nats:4222";
    private static final String DEFAULT_REDIS_URL = "redis://redis:6379";
    private static final int DEFAULT_HEALTH_PORT = 8081;

    public static void main(String[] args) {
        String natsUrl = System.getenv().getOrDefault("NATS_URL", DEFAULT_NATS_URL);
        String redisUrl = System.getenv().getOrDefault("REDIS_URL", DEFAULT_REDIS_URL);
        int healthPort = Integer.parseInt(System.getenv().getOrDefault("HEALTH_PORT", String.valueOf(DEFAULT_HEALTH_PORT)));

        MessageBus bus = NatsMessageBus.connect(natsUrl);
        Runtime.getRuntime().addShutdownHook(new Thread(bus::close));

        RedisConnectionProvider redis = new RedisConnectionProvider(redisUrl);
        Runtime.getRuntime().addShutdownHook(new Thread(redis::close));

        RoomLifecycleClient roomLifecycle = new RoomLifecycleClient(bus);
        MatchmakingService matchmakingService = new MatchmakingServiceImpl(
                new EloRangeMatchCriteria(ELO_MATCH_RANGE), new BusRoomAllocationClient(roomLifecycle),
                new RedisWaitingPlayersQueue(redis));
        new MatchmakerRuntime(bus, matchmakingService).start();
        HealthServer.startOnPort(healthPort);

        System.out.println("[matchmaker] connected to " + natsUrl + " and " + redisUrl
                + ", waiting for matchmaking requests...");
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
