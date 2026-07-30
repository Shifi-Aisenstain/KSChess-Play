package Test.unit;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import server.apigateway.LoginRestHandler;
import server.auth.AuthService;
import server.auth.User;
import shared.protocol.MessageCodec;
import shared.protocol.payload.LoginPayload;
import shared.protocol.payload.LoginResultPayload;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Exercises PLAN_split_6_services.md §3.5's REST endpoint against a real loopback
 * {@code com.sun.net.httpserver.HttpServer} (rather than a hand-built {@code HttpExchange} fake -
 * see {@link LoginRestHandler}'s javadoc), the same "real thing on localhost" approach
 * {@code TestSupport.LoopbackServer} already uses for the WS side.
 */
public class LoginRestHandlerTest {

    private static final class StubAuthService implements AuthService {
        Result nextResult;
        @Override public Result login(String username, String password) { return nextResult; }
    }

    private HttpServer server;
    private StubAuthService authService;
    private HttpClient client;
    private URI loginUri;

    @BeforeEach
    public void setUp() throws IOException {
        authService = new StubAuthService();
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/login", new LoginRestHandler(authService));
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
    }

    @Test
    public void testFailedLoginStillReturns200WithSuccessFalse() throws Exception {
        authService.nextResult = AuthService.Result.fail("bad password");

        HttpResponse<String> response = post(MessageCodec.encodeInnerPayload(new LoginPayload("alice", "wrong")));

        assertEquals(200, response.statusCode());
        LoginResultPayload payload = MessageCodec.decodeInnerPayload(response.body(), LoginResultPayload.class);
        assertFalse(payload.success);
        assertEquals("bad password", payload.errorMessage);
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
}
