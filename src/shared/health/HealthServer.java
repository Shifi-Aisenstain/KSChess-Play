package shared.health;

import com.sun.net.httpserver.HttpServer;
import shared.metrics.MetricsRegistry;
import shared.metrics.MetricsRestHandler;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;

public final class HealthServer {
    private HealthServer() { }

    public static HttpServer startOnPort(int port, MetricsRegistry metricsRegistry) {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
            server.createContext("/health", new HealthRestHandler());
            server.createContext("/metrics", new MetricsRestHandler(metricsRegistry));
            server.setExecutor(null);
            server.start();
            return server;
        } catch (IOException e) {
            throw new UncheckedIOException("Could not start health-check server on port " + port, e);
        }
    }
}
