package Test.unit;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import shared.health.HealthServer;
import shared.metrics.MetricsRegistry;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

public class HealthServerTest {

    private HttpServer server;

    @AfterEach
    public void tearDown() {
        if (server != null) server.stop(0);
    }

    @Test
    public void testHealthEndpointReturns200Ok() throws Exception {
        server = HealthServer.startOnPort(0, new MetricsRegistry());
        int port = server.getAddress().getPort();

        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/health"))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();
        HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertEquals("OK", response.body());
    }

    @Test
    public void testMetricsEndpointIsAlsoMountedOnTheSameServer() throws Exception {
        MetricsRegistry registry = new MetricsRegistry();
        registry.incrementCounter("test_counter_total", "A test counter");
        server = HealthServer.startOnPort(0, registry);
        int port = server.getAddress().getPort();

        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/metrics"))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();
        HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("test_counter_total 1"));
    }
}
