package Test.unit;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import shared.metrics.MetricsRegistry;
import shared.metrics.MetricsRestHandler;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

public class MetricsRestHandlerTest {

    private HttpServer server;
    private MetricsRegistry registry;
    private HttpClient client;
    private URI metricsUri;

    @BeforeEach
    public void setUp() throws Exception {
        registry = new MetricsRegistry();
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/metrics", new MetricsRestHandler(registry));
        server.setExecutor(null);
        server.start();
        client = HttpClient.newHttpClient();
        metricsUri = URI.create("http://localhost:" + server.getAddress().getPort() + "/metrics");
    }

    @AfterEach
    public void tearDown() {
        server.stop(0);
    }

    @Test
    public void testMetricsEndpointReturnsPrometheusTextFormat() throws Exception {
        registry.incrementCounter("login_attempts_total", "Total login attempts");

        HttpRequest request = HttpRequest.newBuilder(metricsUri).timeout(Duration.ofSeconds(5)).GET().build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertTrue(response.headers().firstValue("Content-Type").orElse("").startsWith("text/plain"));
        assertTrue(response.body().contains("login_attempts_total 1"));
    }

    @Test
    public void testEmptyRegistryStillReturns200WithEmptyBody() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(metricsUri).timeout(Duration.ofSeconds(5)).GET().build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertEquals("", response.body());
    }
}
