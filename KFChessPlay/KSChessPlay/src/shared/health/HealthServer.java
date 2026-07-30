package shared.health;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;

/**
 * Starts a tiny standalone HTTP server exposing only {@code /health} (PLAN_split_6_services.md §3.6),
 * for processes that don't otherwise run an HTTP server of their own: the Shard, WS Gateway,
 * Matchmaker, and Game Allocator all call this from their {@code *Main.main()}.
 * {@code server.apigateway.ApiGatewayMain} already runs a real {@code HttpServer} for {@code /login}
 * and just adds a {@code /health} context directly onto that one instead of using this class.
 */
public final class HealthServer {
    private HealthServer() { }

    /** @param port bind port; {@code 0} picks an ephemeral port (used by tests) - read it back via
     *              the returned {@link HttpServer#getAddress()}. */
    public static HttpServer startOnPort(int port) {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
            server.createContext("/health", new HealthRestHandler());
            server.setExecutor(null);
            server.start();
            return server;
        } catch (IOException e) {
            throw new UncheckedIOException("Could not start health-check server on port " + port, e);
        }
    }
}
