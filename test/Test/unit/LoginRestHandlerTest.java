package Test.unit;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import server.apigateway.LoginRestHandler;
import server.auth.AuthService;
import server.auth.InMemorySessionStore;
import server.auth.User;
import shared.metrics.MetricsRegistry;
import shared.protocol.MessageCodec;
import shared.protocol.payload.LoginPayload;
import shared.protocol.payload.LoginResultPayload;
import shared.tracing.JaegerReporter;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

public class LoginRestHandlerTest {

    private static final class StubAuthService implements AuthService {
        Result nextResult;
        @Override public Result login(String username, String password) { return nextResult; }
    }

    private HttpServer server;
    private StubAuthService authService;
    private HttpClient client;
    private URI loginUri;
    private MetricsRegistry metricsRegistry;

    @BeforeEach
    public void setUp() throws IOException {
        authService = new StubAuthService();
        metricsRegistry = new MetricsRegistry();
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/login", new LoginRestHandler(authService, TestSupport.newActivityLoggerOrThrow(), metricsRegistry,
                new InMemorySessionStore(), new JaegerReporter("http://localhost:1/not-a-real-jaeger")));
        server.setExecutor(null);
        server.start();
        client = HttpClient.newHttpClient();
        loginUri = URI.create("http://localhost:" + server.getAddress().getPort() + "/login");
    }

    @AfterEach
    public void tearDown() {
        server.stop(0);
    }

    private HttpResponse<String> post(String jsonBody) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(loginUri)
                .timeout(Duration.ofSeconds(5))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    @Test
    public void testSuccessfulLoginReturns200WithUserDetails() throws Exception {
        authService.nextResult = AuthService.Result.ok(new User(1, "alice", 1200));

        HttpResponse<String> response = post(MessageCodec.encodeInnerPayload(new LoginPayload("alice", "pw")));

        assertEquals(200, response.statusCode());
        LoginResultPayload payload = MessageCodec.decodeInnerPayload(response.body(), LoginResultPayload.class);
        assertTrue(payload.success);
        assertEquals("alice", payload.username);
        assertEquals(1200, payload.elo);
        assertNotNull(payload.sessionToken, "a successful login must issue a session token for later IDENTIFY calls");
    }

    @Test
    public void testFailedLoginStillReturns200WithSuccessFalse() throws Exception {
        authService.nextResult = AuthService.Result.fail("bad password");

        HttpResponse<String> response = post(MessageCodec.encodeInnerPayload(new LoginPayload("alice", "wrong")));

        assertEquals(200, response.statusCode());
        LoginResultPayload payload = MessageCodec.decodeInnerPayload(response.body(), LoginResultPayload.class);
        assertFalse(payload.success);
        assertEquals("bad password", payload.errorMessage);
        assertNull(payload.sessionToken, "a failed login must not issue a session token");
    }

    @Test
    public void testNonPostMethodRejected() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(loginUri).timeout(Duration.ofSeconds(5)).GET().build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(405, response.statusCode());
    }

    @Test
    public void testMalformedBodyRejected() throws Exception {
        HttpResponse<String> response = post("not valid json{{{");

        assertEquals(400, response.statusCode());
    }

    @Test
    public void testMetricsCountLoginAttemptsAndOutcomes() throws Exception {
        authService.nextResult = AuthService.Result.ok(new User(1, "alice", 1200));
        post(MessageCodec.encodeInnerPayload(new LoginPayload("alice", "pw")));

        authService.nextResult = AuthService.Result.fail("bad password");
        post(MessageCodec.encodeInnerPayload(new LoginPayload("alice", "wrong")));

        String rendered = metricsRegistry.renderPrometheusText();
        assertTrue(rendered.contains("apigateway_login_attempts_total 2"));
        assertTrue(rendered.contains("apigateway_login_success_total 1"));
        assertTrue(rendered.contains("apigateway_login_failure_total 1"));
    }
}
