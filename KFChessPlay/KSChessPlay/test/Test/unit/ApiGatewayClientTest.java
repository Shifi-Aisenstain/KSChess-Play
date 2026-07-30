package Test.unit;

import client.net.ApiGatewayClient;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import server.apigateway.LoginRestHandler;
import server.auth.AuthService;
import server.auth.User;
import shared.protocol.payload.LoginResultPayload;

import java.io.IOException;
import java.net.InetSocketAddress;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Exercises {@link ApiGatewayClient} (the client's counterpart to {@code server.apigateway}) against
 * a real loopback {@code HttpServer} running the real {@code LoginRestHandler} - proves the two ends
 * of PLAN_split_6_services.md §3.5's REST contract actually agree on wire format, not just that each
 * side round-trips its own DTOs correctly in isolation.
 */
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
        server.createContext("/login", new LoginRestHandler(authService));
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
        server.stop(0); // now nothing is listening on this port

        assertThrows(IOException.class, () -> client.login("alice", "pw"),
                "Main.connectAndLogin is the one that turns this into a user-facing message");
    }
}
