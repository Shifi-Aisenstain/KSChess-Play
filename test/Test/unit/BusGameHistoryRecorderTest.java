package Test.unit;

import org.junit.jupiter.api.Test;
import server.history.GameHistoryRecorder;
import server.persistence.BusGameHistoryRecorder;
import shared.bus.InMemoryMessageBus;
import shared.bus.MessageBus;
import shared.bus.RoomBusSubjects;
import shared.protocol.MessageCodec;
import shared.protocol.payload.GameRecordedPayload;
import shared.protocol.payload.MoveRecordedPayload;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

public class BusGameHistoryRecorderTest {

    @Test
    public void testRecordMovePublishesExpectedPayload() {
        MessageBus bus = new InMemoryMessageBus();
        AtomicReference<MoveRecordedPayload> captured = new AtomicReference<>();
        bus.subscribe(RoomBusSubjects.MOVE_HISTORY, (message, replyTo) ->
                captured.set(MessageCodec.decodePayload(message, MoveRecordedPayload.class)));

        GameHistoryRecorder recorder = new BusGameHistoryRecorder(bus);
        recorder.recordMove("ROOM1", "Qe2xe5");

        assertNotNull(captured.get());
        assertEquals("ROOM1", captured.get().roomId);
        assertEquals("Qe2xe5", captured.get().notation);
        assertTrue(captured.get().playedAt > 0);
    }

    @Test
    public void testRecordGameResultPublishesExpectedPayload() {
        MessageBus bus = new InMemoryMessageBus();
        AtomicReference<GameRecordedPayload> captured = new AtomicReference<>();
        bus.subscribe(RoomBusSubjects.GAME_HISTORY, (message, replyTo) ->
                captured.set(MessageCodec.decodePayload(message, GameRecordedPayload.class)));

        GameHistoryRecorder recorder = new BusGameHistoryRecorder(bus);
        recorder.recordGameResult("ROOM1", 1L, 2L, "alice", "bob", "WHITE", "checkmate", 16, -16);

        assertNotNull(captured.get());
        assertEquals("ROOM1", captured.get().roomId);
        assertEquals(1L, captured.get().whiteUserId);
        assertEquals(2L, captured.get().blackUserId);
        assertEquals("alice", captured.get().whiteUsername);
        assertEquals("bob", captured.get().blackUsername);
        assertEquals("WHITE", captured.get().winnerColor);
        assertEquals("checkmate", captured.get().reason);
        assertEquals(16, captured.get().whiteEloDelta);
        assertEquals(-16, captured.get().blackEloDelta);
    }

    @Test
    public void testRecordGameResultWithNullWinnerColorSurvivesTheBusRoundTrip() {
        MessageBus bus = new InMemoryMessageBus();
        AtomicReference<GameRecordedPayload> captured = new AtomicReference<>();
        bus.subscribe(RoomBusSubjects.GAME_HISTORY, (message, replyTo) ->
                captured.set(MessageCodec.decodePayload(message, GameRecordedPayload.class)));

        GameHistoryRecorder recorder = new BusGameHistoryRecorder(bus);
        recorder.recordGameResult("ROOM2", 3L, 4L, "carol", "dave", null, "draw", 0, 0);

        assertNotNull(captured.get());
        assertNull(captured.get().winnerColor, "a draw's null winnerColor must survive JSON encode/decode over the bus as null, not a literal \"null\" string");
        assertEquals("draw", captured.get().reason);
    }
}
