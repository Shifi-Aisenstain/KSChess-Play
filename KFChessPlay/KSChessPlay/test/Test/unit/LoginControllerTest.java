package Test.unit;

import client.bridge.LoginController;
import client.logging.ClientActivityLogger;
import client.net.ServerConnection;
import client.net.ServerMessageListener;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import shared.protocol.MessageCodec;
import shared.protocol.MessageType;
import shared.protocol.payload.LoginResultPayload;

import java.net.URI;
import java.nio.file.Files;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** Like NetworkGameControllerOutgoingTest: the LOGIN send needs an actually-open socket, so this
  * uses TestSupport.LoopbackServer rather than mocking ServerConnection (which is final). */
public class LoginControllerTest {

    private TestSupport.LoopbackServer server;
    private ServerConnection connection;
    private LoginController controller;

    @BeforeEach
    public void setUp() throws Exception {
        server = TestSupport.startLoopbackServer();
        ClientActivityLogger logger = new ClientActivityLogger(
                Files.createTempFile("kfchess-test-login-controller-", ".log").toString());
        ServerMessageListener[] holder = new ServerMessageListener[1];
        connection = new ServerConnection(new URI("ws://localhost:" + server.getPort()),
                m -> holder[0].onMessage(m), logger);
        controller = new LoginController(connection);
        holder[0] = controller;
        assertTrue(connection.connectBlocking(5, TimeUnit.SECONDS));
    }

    @AfterEach
    public void tearDown() throws Exception {
        connection.closeBlocking();
        server.stop();
    }

    @Test
    public void testLoginSendsLoginMessageWithCredentials() throws Exception {
        controller.login("alice", "hunter2");

        server.awaitReceived(1, 5);
        var message = MessageCodec.decodeEnvelope(server.received.get(0));
        assertEquals(MessageType.LOGIN, message.getType());
        var payload = MessageCodec.decodePayload(message, shared.protocol.payload.LoginPayload.class);
        assertEquals("alice", payload.username);
        assertEquals("hunter2", payload.password);
    }

    @Test
    public void testFutureCompletesWhenLoginResultArrives() throws Exception {
        CompletableFuture<LoginResultPayload> future = controller.login("alice", "hunter2");
        server.awaitReceived(1, 5);

        server.sendToLastConnection(MessageCodec.encode(MessageType.LOGIN_RESULT,
                LoginResultPayload.ok(1, "alice", 1200)));

        LoginResultPayload result = future.get(5, TimeUnit.SECONDS);
        assertTrue(result.success);
        assertEquals("alice", result.username);
    }

    @Test
    public void testNonLoginResultMessagesAreIgnored() throws Exception {
        CompletableFuture<LoginResultPayload> future = controller.login("alice", "hunter2");
        server.awaitReceived(1, 5);

        server.sendToLastConnection(MessageCodec.encode(MessageType.PONG, shared.protocol.payload.EmptyPayload.INSTANCE));
        Thread.sleep(200);

        assertFalse(future.isDone());
    }
}
