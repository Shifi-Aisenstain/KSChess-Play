package Test.unit;

import org.junit.jupiter.api.Test;
import server.history.GameHistoryRepository;
import server.history.GameSummary;
import server.history.MoveEntry;
import server.persistence.GameHistoryWriterRuntime;
import shared.bus.InMemoryMessageBus;
import shared.bus.MessageBus;
import shared.bus.RoomBusSubjects;
import shared.metrics.MetricsRegistry;
import shared.protocol.MessageType;
import shared.protocol.payload.GameRecordedPayload;
import shared.protocol.payload.MoveRecordedPayload;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

public class GameHistoryWriterRuntimeTest {

    private static final class RecordingRepository implements GameHistoryRepository {
        final CopyOnWriteArrayList<String> savedGameRoomIds = new CopyOnWriteArrayList<>();
        final CopyOnWriteArrayList<String> savedMoveNotations = new CopyOnWriteArrayList<>();
        volatile boolean throwOnSaveMove = false;

        @Override
        public void saveGame(String roomId, long whiteUserId, long blackUserId, String whiteUsername, String blackUsername,
                              String winnerColor, String reason, int whiteEloDelta, int blackEloDelta, long endedAt) {
            savedGameRoomIds.add(roomId);
        }

        @Override
        public void saveMove(String roomId, String notation, long playedAt) {
            if (throwOnSaveMove) throw new IllegalStateException("boom");
            savedMoveNotations.add(notation);
        }

        @Override
        public List<GameSummary> findGamesByUserId(long userId) {
            return List.of();
        }

        @Override
        public List<MoveEntry> findMovesByRoomId(String roomId) {
            return List.of();
        }
    }

    private static void awaitTrue(java.util.function.BooleanSupplier condition, long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (!condition.getAsBoolean() && System.currentTimeMillis() < deadline) {
            Thread.sleep(10);
        }
    }

    @Test
    public void testGameRecordedMessageIsSaved() throws InterruptedException {
        MessageBus bus = new InMemoryMessageBus();
        RecordingRepository repository = new RecordingRepository();
        new GameHistoryWriterRuntime(bus, repository).start();

        bus.publish(RoomBusSubjects.GAME_HISTORY, MessageType.GAME_RECORDED,
                new GameRecordedPayload("ROOM1", 1L, 2L, "alice", "bob", "WHITE", "checkmate", 16, -16, 12345L));

        awaitTrue(() -> !repository.savedGameRoomIds.isEmpty(), 1000);
        assertEquals("ROOM1", repository.savedGameRoomIds.get(0));
    }

    @Test
    public void testMoveRecordedMessageIsSaved() throws InterruptedException {
        MessageBus bus = new InMemoryMessageBus();
        RecordingRepository repository = new RecordingRepository();
        new GameHistoryWriterRuntime(bus, repository).start();

        bus.publish(RoomBusSubjects.MOVE_HISTORY, MessageType.MOVE_RECORDED,
                new MoveRecordedPayload("ROOM1", "Qe2xe5", 12345L));

        awaitTrue(() -> !repository.savedMoveNotations.isEmpty(), 1000);
        assertEquals("Qe2xe5", repository.savedMoveNotations.get(0));
    }

    @Test
    public void testRepositoryFailureDoesNotCrashTheSubscriber() throws InterruptedException {
        MessageBus bus = new InMemoryMessageBus();
        RecordingRepository repository = new RecordingRepository();
        repository.throwOnSaveMove = true;
        new GameHistoryWriterRuntime(bus, repository).start();

        assertDoesNotThrow(() -> bus.publish(RoomBusSubjects.MOVE_HISTORY, MessageType.MOVE_RECORDED,
                new MoveRecordedPayload("ROOM1", "Qe2xe5", 12345L)));
    }

    @Test
    public void testMetricsCountRecordedAndFailedGamesAndMoves() throws InterruptedException {
        MessageBus bus = new InMemoryMessageBus();
        RecordingRepository repository = new RecordingRepository();
        MetricsRegistry metricsRegistry = new MetricsRegistry();
        new GameHistoryWriterRuntime(bus, repository, metricsRegistry).start();

        bus.publish(RoomBusSubjects.GAME_HISTORY, MessageType.GAME_RECORDED,
                new GameRecordedPayload("ROOM1", 1L, 2L, "alice", "bob", "WHITE", "checkmate", 16, -16, 12345L));
        bus.publish(RoomBusSubjects.MOVE_HISTORY, MessageType.MOVE_RECORDED,
                new MoveRecordedPayload("ROOM1", "e4", 1L));

        awaitTrue(() -> !repository.savedGameRoomIds.isEmpty() && !repository.savedMoveNotations.isEmpty(), 1000);

        String rendered = metricsRegistry.renderPrometheusText();
        assertTrue(rendered.contains("elowriter_games_recorded_total 1"));
        assertTrue(rendered.contains("elowriter_moves_recorded_total 1"));
    }

    @Test
    public void testMetricsCountMoveRecordFailures() throws InterruptedException {
        MessageBus bus = new InMemoryMessageBus();
        RecordingRepository repository = new RecordingRepository();
        repository.throwOnSaveMove = true;
        MetricsRegistry metricsRegistry = new MetricsRegistry();
        new GameHistoryWriterRuntime(bus, repository, metricsRegistry).start();

        bus.publish(RoomBusSubjects.MOVE_HISTORY, MessageType.MOVE_RECORDED,
                new MoveRecordedPayload("ROOM1", "e4", 1L));

        awaitTrue(() -> metricsRegistry.renderPrometheusText().contains("elowriter_move_record_failures_total"), 1000);

        assertTrue(metricsRegistry.renderPrometheusText().contains("elowriter_move_record_failures_total 1"));
    }
}
