package server.shard;

import server.logging.ServerActivityLogger;
import server.persistence.BusEloWriter;
import server.rating.EloRatingService;
import server.rating.EloWriter;
import server.rating.RatingService;
import shared.bus.MessageBus;
import shared.bus.NatsMessageBus;
import shared.eventbus.EventBus;
import shared.health.HealthServer;

/**
 * Composition root / entry point for a Game Server Shard (PLAN_split_6_services.md §3.1) - the
 * process that actually runs {@code GameSession}s, connected only to NATS. Mirrors
 * {@code server.apigateway.ApiGatewayMain}'s role as a composition root, just for this process.
 *
 * <p>Since Server_Design.md step 5, this process needs no SQLite/Postgres connection at all: Elo
 * updates are published over NATS ({@link BusEloWriter}) instead of written synchronously through
 * {@code UserRepository} - the actual write happens later, batched, in a separate process
 * ({@code server.persistence.EloWriterMain}). Before step 5, this Shard held its own
 * {@code SqliteUserRepository} copy and wrote Elo straight through it, against the same file the WS/
 * API Gateway used concurrently - a known rough edge, now resolved by removing this process from the
 * database entirely rather than carrying it forward.
 *
 * <p>Not unit tested directly for the same reason its siblings aren't (see pom.xml's jacoco
 * excludes) - it needs a live NATS server to run at all. Its actual logic lives in
 * {@link GameShardRuntime}, which <i>is</i> unit tested against {@code shared.bus.InMemoryMessageBus}.
 */
public final class GameServerShardMain {
    private static final String DEFAULT_NATS_URL = "nats://nats:4222";
    private static final String DEFAULT_SHARD_ID = "shard-1";
    private static final String ACTIVITY_LOG_FILE = "shard_activity.log";
    private static final int DEFAULT_HEALTH_PORT = 8081;

    public static void main(String[] args) {
        String natsUrl = args.length > 0 ? args[0]
                : System.getenv().getOrDefault("NATS_URL", DEFAULT_NATS_URL);
        // Must match one of the ids server.allocator.GameAllocatorMain's own SHARD_IDS list is
        // configured with, or this Shard will never be picked (see GameShardRuntime's shardId filter).
        String shardId = System.getenv().getOrDefault("SHARD_ID", DEFAULT_SHARD_ID);
        int healthPort = Integer.parseInt(System.getenv().getOrDefault("HEALTH_PORT", String.valueOf(DEFAULT_HEALTH_PORT)));

        EventBus eventBus = new EventBus();
        new ServerActivityLogger(ACTIVITY_LOG_FILE, eventBus);
        RatingService ratingService = new EloRatingService();

        MessageBus bus = NatsMessageBus.connect(natsUrl);
        EloWriter eloWriter = new BusEloWriter(bus);
        GameShardRuntime runtime = new GameShardRuntime(bus, eventBus, ratingService, eloWriter, shardId);
        runtime.start();
        HealthServer.startOnPort(healthPort);

        System.out.println("[shard " + shardId + "] connected to " + natsUrl + ", waiting for room.allocated...");
        Runtime.getRuntime().addShutdownHook(new Thread(bus::close));
        blockForever();
    }

    /** NATS's own dispatcher threads aren't daemon threads, so the JVM would stay alive without
     *  this too - but parking the main thread explicitly makes that dependency visible instead of
     *  implicit, and gives a clean place to hang a shutdown hook. */
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
