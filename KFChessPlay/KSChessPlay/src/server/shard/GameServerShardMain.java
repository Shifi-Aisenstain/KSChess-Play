package server.shard;

import server.auth.SqliteUserRepository;
import server.auth.UserRepository;
import server.config.PersistenceConfig;
import server.logging.ServerActivityLogger;
import server.rating.EloRatingService;
import server.rating.RatingService;
import shared.bus.MessageBus;
import shared.bus.NatsMessageBus;
import shared.eventbus.EventBus;

/**
 * Composition root / entry point for a Game Server Shard (PLAN_split_6_services.md §3.1) - the
 * process that actually runs {@code GameSession}s, connected only to NATS and (for now) the same
 * SQLite file the monolith uses. Mirrors {@code server.ServerController}'s role as a composition
 * root, just for this process instead of the monolith.
 *
 * <p><b>Known limitation, called out explicitly in the plan (§4):</b> this Shard writes Elo updates
 * straight through {@link SqliteUserRepository} against the same {@code kungfu_chess.db} file the
 * monolith uses - synchronously, exactly like today, because {@code PostgresUserRepository} and the
 * async NATS-based persistence path are §3.5's job, not this one's. Running the monolith and one or
 * more Shards against the same SQLite file concurrently is a known rough edge accepted for this
 * sub-step; it's the reason §3.5 exists.
 *
 * <p>Not unit tested directly for the same reason {@code server.ServerController} isn't (see
 * pom.xml's jacoco excludes) - it needs a live NATS server and a real SQLite file to run at all.
 * Its actual logic lives in {@link GameShardRuntime}, which <i>is</i> unit tested against
 * {@code shared.bus.InMemoryMessageBus}.
 */
public final class GameServerShardMain {
    private static final String DEFAULT_NATS_URL = "nats://nats:4222";
    private static final String DEFAULT_DB_FILE = "kungfu_chess.db";
    private static final String ACTIVITY_LOG_FILE = "shard_activity.log";

    public static void main(String[] args) {
        String natsUrl = args.length > 0 ? args[0]
                : System.getenv().getOrDefault("NATS_URL", DEFAULT_NATS_URL);
        // Overridable so docker-compose can point this at the same file as server.ServerController
        // via a shared volume - see the class javadoc above for why they need to agree on one file.
        String dbFile = System.getenv().getOrDefault("DB_FILE", DEFAULT_DB_FILE);

        EventBus eventBus = new EventBus();
        ServerActivityLogger activityLogger = new ServerActivityLogger(ACTIVITY_LOG_FILE, eventBus);
        UserRepository userRepository = PersistenceConfig.initialize(dbFile, activityLogger);
        RatingService ratingService = new EloRatingService();

        MessageBus bus = NatsMessageBus.connect(natsUrl);
        GameShardRuntime runtime = new GameShardRuntime(bus, eventBus, ratingService, userRepository);
        runtime.start();

        System.out.println("[shard] connected to " + natsUrl + ", waiting for room.allocated...");
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
