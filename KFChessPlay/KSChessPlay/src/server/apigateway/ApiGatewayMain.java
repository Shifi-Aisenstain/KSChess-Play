package server.apigateway;

import com.sun.net.httpserver.HttpServer;
import server.auth.AuthService;
import server.auth.AuthServiceImpl;
import server.auth.PasswordHasher;
import server.auth.UserRepository;
import server.config.PersistenceConfig;
import server.logging.ServerActivityLogger;
import shared.eventbus.EventBus;
import shared.health.HealthRestHandler;

import java.io.IOException;
import java.net.InetSocketAddress;

/**
 * Composition root / entry point for the API Gateway (PLAN_split_6_services.md §3.5) - the one
 * process that owns {@link AuthService}/{@link UserRepository} now. Every other process that used to
 * keep its own copy (the WS Gateway since §3.2, this monolith originally) either never needed it for
 * anything but login (removed) or never actually used it (the old {@code server.ServerController}'s
 * copy, deleted in §3.3/§3.4). Exposes {@code POST /login} over plain {@code com.sun.net.httpserver}
 * (built into the JDK, no new dependency - matches this project's no-heavy-framework style) instead
 * of a NATS subject, since login is the one client-facing call the plan places on REST rather than
 * the WS protocol (see §5 of the design doc and §1's "API Gateway (REST/HTTP)" row).
 *
 * <p>Not unit tested directly for the same reason its sibling composition roots aren't (see pom.xml's
 * jacoco excludes) - it needs a real socket/HTTP server to run at all. {@link LoginRestHandler} *is*
 * unit tested, against a real loopback {@code HttpServer} (see {@link LoginRestHandler}'s javadoc).
 */
public final class ApiGatewayMain {
    private static final int DEFAULT_PORT = 8080;
    private static final String ACTIVITY_LOG_FILE = "apigateway_activity.log";

    public static void main(String[] args) throws IOException {
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", String.valueOf(DEFAULT_PORT)));

        EventBus eventBus = new EventBus();
        ServerActivityLogger activityLogger = new ServerActivityLogger(ACTIVITY_LOG_FILE, eventBus);
        UserRepository userRepository = PersistenceConfig.initialize(activityLogger);
        AuthService authService = new AuthServiceImpl(userRepository, new PasswordHasher());

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/login", new LoginRestHandler(authService));
        server.createContext("/health", new HealthRestHandler());
        server.setExecutor(null); // default executor is fine at this scale - see HealthRestHandler's javadoc
        server.start();

        System.out.println("[apigateway] KungFu Chess API Gateway started on port " + port);
    }
}
