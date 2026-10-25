package server.apigateway;

import com.sun.net.httpserver.HttpServer;
import server.auth.AuthService;
import server.auth.AuthServiceImpl;
import server.auth.PasswordHasher;
import server.auth.RedisSessionStore;
import server.auth.SessionStore;
import server.auth.UserRepository;
import server.config.PersistenceConfig;
import server.history.GameHistoryRepository;
import server.logging.ServerActivityLogger;
import shared.eventbus.EventBus;
import shared.health.HealthRestHandler;
import shared.metrics.MetricsRegistry;
import shared.metrics.MetricsRestHandler;
import shared.redis.RedisConnectionProvider;
import shared.tracing.JaegerReporter;

import java.io.IOException;
import java.net.InetSocketAddress;

public final class ApiGatewayMain {
    private static final int DEFAULT_PORT = 8080;
    private static final String DEFAULT_REDIS_URL = "redis://redis:6379";
    private static final String DEFAULT_JAEGER_URL = "http://jaeger:9411/api/v2/spans";
    private static final String ACTIVITY_LOG_FILE = "logs/apigateway/activity.log";

    public static void main(String[] args) throws IOException {
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", String.valueOf(DEFAULT_PORT)));
        String redisUrl = System.getenv().getOrDefault("REDIS_URL", DEFAULT_REDIS_URL);
        String jaegerUrl = System.getenv().getOrDefault("JAEGER_URL", DEFAULT_JAEGER_URL);

        EventBus eventBus = new EventBus();
        ServerActivityLogger activityLogger = new ServerActivityLogger(ACTIVITY_LOG_FILE, eventBus);
        PersistenceConfig.PersistenceBundle persistence = PersistenceConfig.initialize(activityLogger);
        UserRepository userRepository = persistence.users;
        GameHistoryRepository historyRepository = persistence.history;
        AuthService authService = new AuthServiceImpl(userRepository, new PasswordHasher());
        MetricsRegistry metricsRegistry = new MetricsRegistry();

        RedisConnectionProvider redis = new RedisConnectionProvider(redisUrl);
        Runtime.getRuntime().addShutdownHook(new Thread(redis::close));
        SessionStore sessionStore = new RedisSessionStore(redis);
        JaegerReporter jaegerReporter = new JaegerReporter(jaegerUrl);

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/login", new LoginRestHandler(authService, activityLogger, metricsRegistry, sessionStore, jaegerReporter));
        server.createContext("/history", new HistoryRestHandler(historyRepository, activityLogger, metricsRegistry, jaegerReporter));
        server.createContext("/health", new HealthRestHandler());
        server.createContext("/metrics", new MetricsRestHandler(metricsRegistry));
        server.setExecutor(null);
        server.start();

        System.out.println("[apigateway] KungFu Chess API Gateway started on port " + port);
    }
}
