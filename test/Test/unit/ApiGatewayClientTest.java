package Test.unit;

import client.net.ApiGatewayClient;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import server.apigateway.LoginRestHandler;
import server.auth.AuthService;
import server.auth.InMemorySessionStore;
import server.auth.User;
import shared.metrics.MetricsRegistry;
import shared.protocol.payload.LoginResultPayload;
import shared.tracing.JaegerReporter;

import java.io.IOException;
import java.net.InetSocketAddress;

import static org.junit.jupiter.api.Assertions.*;

public class ApiGatewayClientTest {

    private static final class StubAuthService implements AuthService {
        Result nextResult;
        @Override public Result login(String username, String password) { return nextResult; }
    }

    private HttpServer server;
    private StubAuthService authService;
    private ApiGatewayClient client;

    @BeforeEach
    public void setUp() throws IOException {
        authService = new StubAuthService();
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/login", new LoginRestHandler(authService, TestSupport.newActivityLoggerOrThrow(), new MetricsRegistry(),
                new InMemorySessionStore(), new JaegerReporter("http://localhost:1/not-a-real-jaeger")));
        server.setExecutor(null);
        server.start();
        client = new ApiGatewayClient("http://localhost:" + server.getAddress().getPort());
    }

    @AfterEach
    public void tearDown() {
        server.stop(0);
    }

    @Test
    public void testSuccessfulLoginRoundTrips() throws Exception {
        authService.nextResult = AuthService.Result.ok(new User(7, "bob", 1400));

        LoginResultPayload result = client.login("bob", "hunter2");

        assertTrue(result.success);
        assertEquals(7, result.userId);
        assertEquals("bob", result.username);
        assertEquals(1400, result.elo);
        assertNotNull(result.sessionToken);
    }

    @Test
    public void testFailedLoginRoundTrips() throws Exception {
        authService.nextResult = AuthService.Result.fail("no such user");

        LoginResultPayload result = client.login("nobody", "wrong");

        assertFalse(result.success);
        assertEquals("no such user", result.errorMessage);
    }

    @Test
    public void testUnreachableGatewayReturnsFailureNotException() {
        server.stop(0);

        assertThrows(IOException.class, () -> client.login("alice", "pw"),
                "Main.connectAndLogin is the one that turns this into a user-facing message");
    }
}
