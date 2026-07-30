package shared.health;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

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
