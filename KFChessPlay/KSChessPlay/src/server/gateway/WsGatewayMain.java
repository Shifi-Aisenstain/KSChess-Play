package server.gateway;

import server.auth.RedisSessionStore;
import server.auth.SessionStore;
import server.dispatch.MessageDispatcher;
import server.logging.ServerActivityLogger;
import server.net.ChessWebSocketServer;
import server.net.SessionRegistry;
import shared.bus.MessageBus;
import shared.bus.NatsMessageBus;
import shared.eventbus.EventBus;
import shared.health.HealthServer;
import shared.metrics.MetricsRegistry;
import shared.redis.RedisConnectionProvider;
import shared.tracing.JaegerReporter;

import java.net.InetSocketAddress;

public final class WsGatewayMain {
    private static final int DEFAULT_PORT = 8887;
    private static final String ACTIVITY_LOG_FILE = "logs/gateway/activity.log";
    private static final String DEFAULT_NATS_URL = "nats://nats:4222";
    private static final String DEFAULT_REDIS_URL = "redis://redis:6379";
    private static final String DEFAULT_JAEGER_URL = "http://jaeger:9411/api/v2/spans";
    private static final int DEFAULT_HEALTH_PORT = 8081;

    public static void main(String[] args) {
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", String.valueOf(DEFAULT_PORT)));
        String natsUrl = System.getenv().getOrDefault("NATS_URL", DEFAULT_NATS_URL);
        String redisUrl = System.getenv().getOrDefault("REDIS_URL", DEFAULT_REDIS_URL);
        String jaegerUrl = System.getenv().getOrDefault("JAEGER_URL", DEFAULT_JAEGER_URL);
        int healthPort = Integer.parseInt(System.getenv().getOrDefault("HEALTH_PORT", String.valueOf(DEFAULT_HEALTH_PORT)));

        EventBus eventBus = new EventBus();
        ServerActivityLogger activityLogger = new ServerActivityLogger(ACTIVITY_LOG_FILE, eventBus);

        MessageBus bus = NatsMessageBus.connect(natsUrl);
        Runtime.getRuntime().addShutdownHook(new Thread(bus::close));

        RedisConnectionProvider redis = new RedisConnectionProvider(redisUrl);
        Runtime.getRuntime().addShutdownHook(new Thread(redis::close));
        SessionStore sessionStore = new RedisSessionStore(redis);

        GatewayRoomRegistry roomRegistry = new GatewayRoomRegistry(bus);
        RoomLifecycleClient roomLifecycle = new RoomLifecycleClient(bus);
        MatchmakingClient matchmakingClient = new MatchmakingClient(bus);
        MetricsRegistry metricsRegistry = new MetricsRegistry();
        JaegerReporter jaegerReporter = new JaegerReporter(jaegerUrl);

        MessageDispatcher dispatcher = GatewayDispatcherFactory.build(roomLifecycle, matchmakingClient, roomRegistry, sessionStore,
                metricsRegistry);
        SessionRegistry sessionRegistry = new SessionRegistry(activityLogger);

        ChessWebSocketServer server = new ChessWebSocketServer(
                new InetSocketAddress(port), dispatcher, sessionRegistry, roomRegistry, matchmakingClient, activityLogger,
                metricsRegistry, jaegerReporter);
        server.start();
        HealthServer.startOnPort(healthPort, metricsRegistry);
        System.out.println("[gateway] KungFu Chess WS Gateway started on port " + port + ", connected to " + natsUrl);
    }
}
