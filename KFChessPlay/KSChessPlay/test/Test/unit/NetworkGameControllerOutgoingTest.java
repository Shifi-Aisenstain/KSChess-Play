package Test.unit;

import client.bridge.NetworkGameController;
import client.logging.ClientActivityLogger;
import client.net.ServerConnection;
import client.net.ServerMessageListener;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import shared.eventbus.EventBus;
import shared.protocol.Message;
import shared.protocol.MessageCodec;
import shared.protocol.MessageType;

import java.net.URI;
import java.nio.file.Files;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** Covers NetworkGameController's *outgoing* half (requestPlay/handleBoardClick/etc), which needs
  * an actually-open socket to exercise (ServerConnection.send delegates to WebSocketClient.send,
  * which throws on an unconnected client) - see NetworkGameControllerTest for the incoming-message
  * half, which doesn't need one. Uses TestSupport.LoopbackServer (a minimal real WebSocket server)
  * rather than the production server.ServerMain, so this stays independent of SQLite/matchmaking. */
public class NetworkGameControllerOutgoingTest {

    private TestSupport.LoopbackServer server;
    private ServerConnection connection;
    private NetworkGameController controller;

    @BeforeEach
    public void setUp() throws Exception {
        server = TestSupport.startLoopbackServer();
        ClientActivityLogger logger = new ClientActivityLogger(
                Files.createTempFile("kfchess-test-client-outgoing-", ".log").toString());
        ServerMessageListener[] holder = new ServerMessageListener[1];
        connection = new ServerConnection(new URI("ws://localhost:" + server.getPort()),
                m -> holder[0].onMessage(m), logger);
        controller = new NetworkGameController(connection, new EventBus());
        holder[0] = controller;
        assertTrue(connection.connectBlocking(5, TimeUnit.SECONDS));
    }

    @AfterEach
    public void tearDown() throws Exception {
        connection.closeBlocking();
        server.stop();
    }

    // Tracks how many messages we've already consumed, so a second call after another send doesn't
    // race the network and re-read the same (already-arrived) message before the new one lands.
    private int consumed = 0;

    private Message nextReceivedMessage() throws InterruptedException {
        consumed++;
        server.awaitReceived(consumed, 5);
        assertTrue(server.received.size() >= consumed, "server never received message #" + consumed);
        return MessageCodec.decodeEnvelope(server.received.get(consumed - 1));
    }

    @Test
    public void testRequestPlaySendsPlayRequest() throws Exception {
        controller.requestPlay();
        assertEquals(MessageType.PLAY_REQUEST, nextReceivedMessage().getType());
    }

    @Test
    public void testCancelPlaySendsPlayCancel() throws Exception {
        controller.cancelPlay();
        assertEquals(MessageType.PLAY_CANCEL, nextReceivedMessage().getType());
    }

    @Test
    public void testRequestCreateRoomSendsRoomCreateWithId() throws Exception {
        controller.requestCreateRoom("MYROOM");
        Message message = nextReceivedMessage();
        assertEquals(MessageType.ROOM_CREATE, message.getType());
        var payload = MessageCodec.decodePayload(message, shared.protocol.payload.RoomActionPayload.class);
        assertEquals("MYROOM", payload.roomId);
    }

    @Test
    public void testRequestJoinRoomSendsRoomJoinWithId() throws Exception {
        controller.requestJoinRoom("ABC123");
        Message message = nextReceivedMessage();
        assertEquals(MessageType.ROOM_JOIN, message.getType());
        var payload = MessageCodec.decodePayload(message, shared.protocol.payload.RoomActionPayload.class);
        assertEquals("ABC123", payload.roomId);
    }

    @Test
    public void testResignSendsResign() throws Exception {
        controller.resign();
        assertEquals(MessageType.RESIGN, nextReceivedMessage().getType());
    }

    @Test
    public void testFirstBoardClickSendsSelectTrue() throws Exception {
        controller.handleBoardClick(3, 4);
        Message message = nextReceivedMessage();
        assertEquals(MessageType.SELECT, message.getType());
        var payload = MessageCodec.decodePayload(message, shared.protocol.payload.SelectPayload.class);
        assertTrue(payload.selected);
        assertEquals(3, payload.row);
        assertEquals(4, payload.col);
    }

    @Test
    public void testSecondBoardClickOnDifferentSquareSendsMove() throws Exception {
        controller.handleBoardClick(6, 4);
        nextReceivedMessage(); // consume the SELECT

        controller.handleBoardClick(4, 4);
        server.awaitReceived(3, 5); // MOVE, then a deselect SELECT(false)

        boolean sawMove = server.received.stream()
                .map(MessageCodec::decodeEnvelope)
                .anyMatch(m -> m.getType() == MessageType.MOVE);
        assertTrue(sawMove);
    }

    @Test
    public void testClickingSameSquareTwiceDeselects() throws Exception {
        controller.handleBoardClick(3, 3);
        nextReceivedMessage();

        controller.handleBoardClick(3, 3);
        Message message = nextReceivedMessage();
        assertEquals(MessageType.SELECT, message.getType());
        var payload = MessageCodec.decodePayload(message, shared.protocol.payload.SelectPayload.class);
        assertFalse(payload.selected);
    }

    @Test
    public void testRightClickSendsJump() throws Exception {
        controller.handleBoardRightClick(2, 2);
        Message message = nextReceivedMessage();
        assertEquals(MessageType.JUMP, message.getType());
        var payload = MessageCodec.decodePayload(message, shared.protocol.payload.JumpCommandPayload.class);
        assertEquals(2, payload.row);
        assertEquals(2, payload.col);
    }
}
