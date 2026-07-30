package shared.health;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Trivial liveness probe (PLAN_split_6_services.md §3.6): every process exposes this on a small
 * {@code com.sun.net.httpserver.HttpServer} so a container orchestrator can tell "is this process
 * alive at all." Deliberately not a readiness/dependency check (e.g. "is NATS actually reachable") -
 * real dashboards/metrics are explicitly out of scope until later (Server_Design.md §8 and beyond,
 * per the plan's own framing); this is just enough for a Docker/Compose/K8s health check.
 */
public final class HealthRestHandler implements HttpHandler {
    private static final byte[] BODY = "OK".getBytes(StandardCharsets.UTF_8);

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().add("Content-Type", "text/plain; charset=utf-8");
        exchange.sendResponseHeaders(200, BODY.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(BODY);
        }
    }
}
