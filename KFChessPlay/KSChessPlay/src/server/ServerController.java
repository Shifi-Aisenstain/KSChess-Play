package server;

import server.auth.UserRepository;
import server.config.GameServicesConfig;
import server.config.NetworkConfig;
import server.config.PersistenceConfig;
import server.logging.ServerActivityLogger;
import shared.bus.MessageBus;
import shared.bus.NatsMessageBus;
import shared.eventbus.EventBus;

/**
 * Composition root / entry point: assembles the app's top-level parts and starts the server.
 * Renamed from {@code ServerMain} - it doesn't know *how* any individual part is built anymore.
 * Persistence wiring lives in {@link PersistenceConfig}, gameplay-service wiring in
 * {@link GameServicesConfig}, and network/dispatcher wiring in {@link NetworkConfig}. That split
 * is what keeps this file down to "assemble these parts and go" instead of accumulating SQL
 * connection strings, event-bus subscriptions, handler registrations and socket setup all in one
 * place. Nothing below this file reaches upward to construct its own collaborators - that's what
 * keeps every other class swappable/testable in isolation (Dependency Injection by hand, no
 * framework needed for a project this size).
 *
 * <p>Since PLAN_split_6_services.md §3.1, this process also connects to NATS - not to run game
 * logic itself anymore (that moved to {@code server.shard.GameServerShardMain}), but so
 * {@code server.game.RemoteGameSessionHandle} can hand rooms off to whichever Shard is listening.
 */
public final class ServerController {
    private static final int PORT = 8887;
    private static final String DEFAULT_DB_FILE = "kungfu_chess.db";
    private static final String ACTIVITY_LOG_FILE = "server_activity.log";
    private static final String DEFAULT_NATS_URL = "nats://nats:4222";

    public static void main(String[] args) {
        EventBus eventBus = new EventBus();
        ServerActivityLogger activityLogger = new ServerActivityLogger(ACTIVITY_LOG_FILE, eventBus);
        // DB_FILE is overridable so docker-compose can point both this process and
        // server.shard.GameServerShardMain at the same file via a shared volume - see that class's
        // javadoc for why they need to agree on one file for now (§3.5 replaces this with Postgres).
        String dbFile = System.getenv().getOrDefault("DB_FILE", DEFAULT_DB_FILE);
        UserRepository userRepository = PersistenceConfig.initialize(dbFile, activityLogger);

        String natsUrl = System.getenv().getOrDefault("NATS_URL", DEFAULT_NATS_URL);
        MessageBus bus = NatsMessageBus.connect(natsUrl);
        Runtime.getRuntime().addShutdownHook(new Thread(bus::close));

        GameServicesConfig.GameServices services = GameServicesConfig.build(eventBus, userRepository, bus);
        NetworkConfig.startServer(PORT, services, activityLogger);
    }
}
