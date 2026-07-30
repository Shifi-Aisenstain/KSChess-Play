package server.persistence;

import server.auth.UserRepository;
import server.config.PersistenceConfig;
import server.logging.ServerActivityLogger;
import shared.bus.MessageBus;
import shared.bus.NatsMessageBus;
import shared.eventbus.EventBus;
import shared.health.HealthServer;

/**
 * Composition root / entry point for the batched Elo writer (Server_Design.md step 5) - the process
 * that actually drains {@link BusEloWriter}'s NATS messages into storage, in batches, instead of a
 * Game Server Shard writing synchronously on every single game-ended event. Mirrors
 * {@code server.matchmaking.MatchmakerMain}'s role as a composition root, just for this process.
 *
 * <p>The one process besides {@code server.apigateway.ApiGatewayMain} that still touches a database -
 * both go through {@link PersistenceConfig}, so both pick up the same SQLite-vs-Postgres switch
 * ({@code POSTGRES_URL}) independently; docker-compose.yml sets the same value for both so they agree
 * on which storage backend/table they're actually writing to.
 *
 * <p>Not unit tested directly for the same reason its siblings aren't (see pom.xml's jacoco
 * excludes) - it needs a live NATS server (and a real database) to run at all. Its actual logic lives
 * in {@link EloWriterRuntime}, which <i>is</i> unit tested against {@code shared.bus.InMemoryMessageBus}
 * and a fake {@link UserRepository}.
 */
public final class EloWriterMain {
    private static final String DEFAULT_NATS_URL = "nats://nats:4222";
    private static final String ACTIVITY_LOG_FILE = "elowriter_activity.log";
    private static final int DEFAULT_HEALTH_PORT = 8081;

    public static void main(String[] args) {
        String natsUrl = System.getenv().getOrDefault("NATS_URL", DEFAULT_NATS_URL);
        int healthPort = Integer.parseInt(System.getenv().getOrDefault("HEALTH_PORT", String.valueOf(DEFAULT_HEALTH_PORT)));

        EventBus eventBus = new EventBus();
        ServerActivityLogger activityLogger = new ServerActivityLogger(ACTIVITY_LOG_FILE, eventBus);
        UserRepository userRepository = PersistenceConfig.initialize(activityLogger);

        MessageBus bus = NatsMessageBus.connect(natsUrl);
        Runtime.getRuntime().addShutdownHook(new Thread(bus::close));

        new EloWriterRuntime(bus, userRepository).start();
        HealthServer.startOnPort(healthPort);

        System.out.println("[elowriter] connected to " + natsUrl + ", batching Elo updates into storage...");
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
