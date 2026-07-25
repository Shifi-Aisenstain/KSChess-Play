package Test.unit;

import client.bridge.NetworkGameController;
import client.logging.ClientActivityLogger;
import client.net.ServerConnection;
import client.net.ServerMessageListener;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import shared.eventbus.EventBus;
import shared.protocol.Message;
import shared.protocol.MessageCodec;
import shared.protocol.MessageType;
import shared.protocol.payload.CountdownPayload;
import shared.protocol.payload.EmptyPayload;
import shared.protocol.payload.ErrorPayload;
import shared.protocol.payload.GameEventPayload;
import shared.protocol.payload.GameOverPayload;
import shared.protocol.payload.MatchFoundPayload;
import shared.protocol.payload.RoomStatusPayload;
import shared.protocol.payload.StateUpdatePayload;
import view.GameSnapshot;

import java.net.URI;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/** Exercises the incoming half of NetworkGameController (onMessage dispatch + getters) directly,
  * without a live socket. ServerConnection is `final` and its send() requires an actually-open
  * WebSocket, so outgoing methods (requestPlay, handleBoardClick, ...) aren't covered here - only
  * the never-connected constructor (safe: WebSocketClient's ctor does no I/O) is needed to build
  * a NetworkGameController and drive onMessage(...). */
public class NetworkGameControllerTest {

    private NetworkGameController controller;
    private EventBus localEventBus;

    @BeforeEach
    public void setUp() throws Exception {
        // A plain OS-temp path rather than @TempDir: ClientActivityLogger never closes its
        // FileWriter, and @TempDir's auto-cleanup fails on Windows when the file handle is still
        // open - this sidesteps that instead of fighting it.
        String logPath = Files.createTempFile("kfchess-test-client-", ".log").toString();
        ClientActivityLogger logger = new ClientActivityLogger(logPath);
        ServerMessageListener[] holder = new ServerMessageListener[1];
        ServerConnection connection = new ServerConnection(new URI("ws://localhost:1"),
                m -> holder[0].onMessage(m), logger);
        localEventBus = new EventBus();
        controller = new NetworkGameController(connection, localEventBus);
        holder[0] = controller;
    }

    private Message encodeAndDecode(MessageType type, Object payload) {
        return MessageCodec.decodeEnvelope(MessageCodec.encode(type, payload));
    }

    @Test
    public void testInitialStateHasNoSnapshotAndSpectatorColor() {
        assertNull(controller.getLatestSnapshot());
        assertEquals('s', controller.getYourColor());
        assertEquals(8, controller.getBoardRows());
        assertEquals(8, controller.getBoardCols());
    }

    @Test
    public void testPlayWaitingInvokesCallback() {
        AtomicInteger calls = new AtomicInteger();
        controller.setOnPlayWaiting(calls::incrementAndGet);

        controller.onMessage(encodeAndDecode(MessageType.PLAY_WAITING, EmptyPayload.INSTANCE));

        assertEquals(1, calls.get());
    }

    @Test
    public void testMatchFoundDeliversDecodedPayload() {
        AtomicReference<MatchFoundPayload> received = new AtomicReference<>();
        controller.setOnMatchFound(received::set);

        controller.onMessage(encodeAndDecode(MessageType.MATCH_FOUND,
                new MatchFoundPayload("ROOM1", 'w', "bob", 1300)));

        assertNotNull(received.get());
        assertEquals("ROOM1", received.get().roomId);
        assertEquals('w', received.get().assignedColor);
        assertEquals("bob", received.get().opponentUsername);
    }

    @Test
    public void testStateUpdateStoresSnapshotAndFieldsAndFiresOnFirstStateUpdateOnce() {
        AtomicInteger firstUpdateCalls = new AtomicInteger();
        controller.setOnFirstStateUpdate(firstUpdateCalls::incrementAndGet);
        GameSnapshot snapshot = new GameSnapshot(List.of(), false, null, null, List.of(), List.of(), 0, 0, List.of(), List.of());

        controller.onMessage(encodeAndDecode(MessageType.STATE_UPDATE,
                new StateUpdatePayload("ROOM1", 'w', 8, 8, snapshot, "alice", "bob")));
        controller.onMessage(encodeAndDecode(MessageType.STATE_UPDATE,
                new StateUpdatePayload("ROOM1", 'w', 8, 8, snapshot, "alice", "bob")));

        assertNotNull(controller.getLatestSnapshot());
        assertEquals('w', controller.getYourColor());
        assertEquals("alice", controller.getWhiteUsername());
        assertEquals("bob", controller.getBlackUsername());
        assertEquals(1, firstUpdateCalls.get(), "onFirstStateUpdate should only fire once");
    }

    @Test
    public void testGameEventIsRepublishedOnLocalEventBus() {
        AtomicReference<GameEventPayload> received = new AtomicReference<>();
        localEventBus.subscribe(GameEventPayload.class, received::set);

        GameEventPayload event = new GameEventPayload(GameEventPayload.Kind.GAME_STARTED,
                'w', '\0', '\0', "Game started", 0, 0, "");
        controller.onMessage(encodeAndDecode(MessageType.GAME_EVENT, event));

        assertNotNull(received.get());
        assertEquals(GameEventPayload.Kind.GAME_STARTED, received.get().kind);
    }

    @Test
    public void testDisconnectCountdownDeliversPayload() {
        AtomicReference<CountdownPayload> received = new AtomicReference<>();
        controller.setOnDisconnectCountdown(received::set);

        controller.onMessage(encodeAndDecode(MessageType.DISCONNECT_COUNTDOWN,
                new CountdownPayload("ROOM1", 'w', 15)));

        assertEquals(15, received.get().secondsRemaining);
    }

    @Test
    public void testGameOverDeliversPayload() {
        AtomicReference<GameOverPayload> received = new AtomicReference<>();
        controller.setOnGameOver(received::set);

        controller.onMessage(encodeAndDecode(MessageType.GAME_OVER,
                new GameOverPayload("ROOM1", "WHITE", "checkmate", 8, -8, 1208, 1192, "alice", "bob")));

        assertEquals("WHITE", received.get().winnerColor);
    }

    @Test
    public void testServerErrorDeliversMessageString() {
        AtomicReference<String> received = new AtomicReference<>();
        controller.setOnServerError(received::set);

        controller.onMessage(encodeAndDecode(MessageType.ERROR, new ErrorPayload("Room not found")));

        assertEquals("Room not found", received.get());
    }

    @Test
    public void testRoomCreatedDeliversPayload() {
        AtomicReference<RoomStatusPayload> received = new AtomicReference<>();
        controller.setOnRoomCreated(received::set);

        controller.onMessage(encodeAndDecode(MessageType.ROOM_CREATED, new RoomStatusPayload("ROOM1", "WHITE")));

        assertEquals("ROOM1", received.get().roomId);
        assertEquals("WHITE", received.get().role);
    }

    @Test
    public void testPongIsANoOpAndDoesNotThrow() {
        assertDoesNotThrow(() -> controller.onMessage(encodeAndDecode(MessageType.PONG, EmptyPayload.INSTANCE)));
    }

    @Test
    public void testCallbackNotSetDoesNotThrow() {
        // No setOnPlayWaiting(...) called - onMessage should tolerate a null callback.
        assertDoesNotThrow(() -> controller.onMessage(encodeAndDecode(MessageType.PLAY_WAITING, EmptyPayload.INSTANCE)));
    }
}
