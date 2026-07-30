package Test.unit;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import shared.health.HealthServer;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Exercises PLAN_split_6_services.md §3.6's trivial liveness probe against a real loopback server -
 * every process (Shard/Gateway/Matchmaker/Allocator) starts one of these via
 * {@link HealthServer#startOnPort}, and {@code server.apigateway.ApiGatewayMain} wires the same
 * {@code shared.health.HealthRestHandler} directly onto its own existing {@code HttpServer} instead.
 */
public class HealthServerTest {

    private HttpServer server;

    @AfterEach
    public void tearDown() {
        if (server != null) server.stop(0);
    }

    @Test
    public void testHealthEndpointReturns200Ok() throws Exception {
        server = HealthServer.startOnPort(0); // 0 = ephemeral port, avoids clashing with a real service
        int port = server.getAddress().getPort();

        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/health"))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();
        HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertEquals("OK", response.body());
    }
}
