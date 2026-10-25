package server.persistence;

import server.history.GameHistoryRepository;
import shared.bus.MessageBus;
import shared.bus.RoomBusSubjects;
import shared.metrics.MetricsRegistry;
import shared.protocol.MessageCodec;
import shared.protocol.payload.GameRecordedPayload;
import shared.protocol.payload.MoveRecordedPayload;

public final class GameHistoryWriterRuntime {
    private final MessageBus bus;
    private final GameHistoryRepository repository;
    private final MetricsRegistry metricsRegistry;

    public GameHistoryWriterRuntime(MessageBus bus, GameHistoryRepository repository) {
        this(bus, repository, new MetricsRegistry());
    }

    public GameHistoryWriterRuntime(MessageBus bus, GameHistoryRepository repository, MetricsRegistry metricsRegistry) {
        this.bus = bus;
        this.repository = repository;
        this.metricsRegistry = metricsRegistry;
    }

    public void start() {
        bus.subscribe(RoomBusSubjects.GAME_HISTORY, (message, replyTo) -> {
            GameRecordedPayload p = MessageCodec.decodePayload(message, GameRecordedPayload.class);
            try {
                repository.saveGame(p.roomId, p.whiteUserId, p.blackUserId, p.whiteUsername, p.blackUsername,
                        p.winnerColor, p.reason, p.whiteEloDelta, p.blackEloDelta, p.endedAt);
                metricsRegistry.incrementCounter("elowriter_games_recorded_total", "Total games persisted to history storage");
            } catch (RuntimeException e) {
                System.err.println("[game-history-writer] failed to save game " + p.roomId + ": " + e.getMessage());
                metricsRegistry.incrementCounter("elowriter_game_record_failures_total", "Total games that failed to persist");
            }
        });

        bus.subscribe(RoomBusSubjects.MOVE_HISTORY, (message, replyTo) -> {
            MoveRecordedPayload p = MessageCodec.decodePayload(message, MoveRecordedPayload.class);
            try {
                repository.saveMove(p.roomId, p.notation, p.playedAt);
                metricsRegistry.incrementCounter("elowriter_moves_recorded_total", "Total moves persisted to history storage");
            } catch (RuntimeException e) {
                System.err.println("[game-history-writer] failed to save move for room " + p.roomId + ": " + e.getMessage());
                metricsRegistry.incrementCounter("elowriter_move_record_failures_total", "Total moves that failed to persist");
            }
        });
    }
}
