package server.config;

import server.config.GameServicesConfig.GameServices;
import server.dispatch.MessageDispatcher;
import server.logging.ServerActivityLogger;
import server.net.ChessWebSocketServer;
import server.net.SessionRegistry;

import java.net.InetSocketAddress;

/**
 * Wires and starts the network layer: the session registry, the message dispatcher (built by
 * {@link DispatcherFactory}), and the WebSocket server itself. The last piece of wiring
 * {@code ServerController} used to do inline - pulled out here for the same reason as
 * {@link PersistenceConfig}/{@link GameServicesConfig}.
 */
public final class NetworkConfig {
    private NetworkConfig() { }

    public static void startServer(int port, GameServices services, ServerActivityLogger activityLogger) {
        SessionRegistry sessionRegistry = new SessionRegistry(activityLogger);
        MessageDispatcher dispatcher = DispatcherFactory.build(
                services.authService(), services.matchmakingService(), services.roomManager());

        ChessWebSocketServer server = new ChessWebSocketServer(
                new InetSocketAddress(port), dispatcher, sessionRegistry,
                services.roomManager(), services.matchmakingService(), activityLogger);
        server.start();
        System.out.println("KungFu Chess server started on port " + port);
    }
}
