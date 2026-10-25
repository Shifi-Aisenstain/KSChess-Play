package Test.unit;

import models.Piece;
import models.Position;
import org.junit.jupiter.api.Test;
import server.history.GameHistoryRecorder;
import server.shard.ShardGameHistoryRelay;
import shared.eventbus.EventBus;
import shared.eventbus.events.MoveExecutedEvent;

import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

public class ShardGameHistoryRelayTest {

    private static final class RecordingHistoryRecorder implements GameHistoryRecorder {
        final CopyOnWriteArrayList<String> recordedMoves = new CopyOnWriteArrayList<>();

        @Override public void recordMove(String roomId, String notation) {
            recordedMoves.add(roomId + ":" + notation);
        }
        @Override public void recordGameResult(String roomId, long whiteUserId, long blackUserId,
                                                String whiteUsername, String blackUsername,
                                                String winnerColor, String reason,
                                                int whiteEloDelta, int blackEloDelta) {
        }
    }

    @Test
    public void testMoveExecutedEventIsRelayedToHistoryRecorder() {
        EventBus eventBus = new EventBus();
        RecordingHistoryRecorder recorder = new RecordingHistoryRecorder();
        new ShardGameHistoryRelay(eventBus, recorder);

        eventBus.publish(new MoveExecutedEvent("ROOM1", new Piece('w', 'P'), new Position(6, 4),
                new Position(4, 4), null, "e4", 0, 0, "00:00.000|e4"));

        assertEquals(1, recorder.recordedMoves.size());
        assertEquals("ROOM1:e4", recorder.recordedMoves.get(0));
    }
}
