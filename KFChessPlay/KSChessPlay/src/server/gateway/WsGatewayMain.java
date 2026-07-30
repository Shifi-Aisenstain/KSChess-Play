package server.gateway;

import server.dispatch.MessageDispatcher;
import server.logging.ServerActivityLogger;
import server.net.ChessWebSocketServer;
import server.net.SessionRegistry;
import shared.bus.MessageBus;
import shared.bus.NatsMessageBus;
import shared.eventbus.EventBus;
import shared.health.HealthServer;

import java.net.InetSocketAddress;

/**
 * Composition root / entry point for the WS Gateway (PLAN_split_6_services.md §3.2) - the process
 * that holds every client socket. Mirrors {@code server.shard.GameServerShardMain}'s role as a
 * composition root, just for this process: connects to NATS, then wires the transport/dispatch layer
 * ({@link ChessWebSocketServer}/{@link SessionRegistry}/{@link MessageDispatcher}) against the three
 * Gateway-side bus clients ({@link GatewayRoomRegistry}, {@link RoomLifecycleClient},
 * {@link MatchmakingClient}).
 *
 * <p>Since PLAN_split_6_services.md §3.5, this process needs no SQLite connection or
 * {@code AuthService} of its own at all anymore: authentication happens over REST against
 * {@code server.apigateway.ApiGatewayMain} before the client even opens this socket - see
 * {@code server.dispatch.handlers.IdentifyHandler}'s javadoc, which replaced the old
 * password-checking {@code LoginHandler} that used to need this Gateway's own {@code AuthService}
 * copy (a known rough edge from §3.2, now retired rather than carried forward).
 *
 * <p>Not unit tested directly for the same reason {@code GameServerShardMain} isn't (see pom.xml's
 * jacoco excludes) - it needs a live NATS server to run at all, and a real socket to accept
 * connections on.
 */
public final class WsGatewayMain {
    private static final int DEFAULT_PORT = 8887;
    private static final String ACTIVITY_LOG_FILE = "gateway_activity.log";
    private static final String DEFAULT_NATS_URL = "nats://nats:4222";
    private static final int DEFAULT_HEALTH_PORT = 8081;

    public static void main(String[] args) {
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", String.valueOf(DEFAULT_PORT)));
        String natsUrl = System.getenv().getOrDefault("NATS_URL", DEFAULT_NATS_URL);
        int healthPort = Integer.parseInt(System.getenv().getOrDefault("HEALTH_PORT", String.valueOf(DEFAULT_HEALTH_PORT)));

        EventBus eventBus = new EventBus();
        ServerActivityLogger activityLogger = new ServerActivityLogger(ACTIVITY_LOG_FILE, eventBus);

        MessageBus bus = NatsMessageBus.connect(natsUrl);
        Runtime.getRuntime().addShutdownHook(new Thread(bus::close));

        GatewayRoomRegistry roomRegistry = new GatewayRoomRegistry(bus);
        RoomLifecycleClient roomLifecycle = new RoomLifecycleClient(bus);
        MatchmakingClient matchmakingClient = new MatchmakingClient(bus);

        MessageDispatcher dispatcher = GatewayDispatcherFactory.build(roomLifecycle, matchmakingClient, roomRegistry);
        SessionRegistry sessionRegistry = new SessionRegistry(activityLogger);

        ChessWebSocketServer server = new ChessWebSocketServer(
                new InetSocketAddress(port), dispatcher, sessionRegistry, roomRegistry, matchmakingClient, activityLogger);
        server.start();
        HealthServer.startOnPort(healthPort);
        System.out.println("[gateway] KungFu Chess WS Gateway started on port " + port + ", connected to " + natsUrl);
    }
}
