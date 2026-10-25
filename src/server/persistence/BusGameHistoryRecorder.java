package server.persistence;

import server.history.GameHistoryRecorder;
import shared.bus.MessageBus;
import shared.bus.RoomBusSubjects;
import shared.protocol.MessageType;
import shared.protocol.payload.GameRecordedPayload;
import shared.protocol.payload.MoveRecordedPayload;

public final class BusGameHistoryRecorder implements GameHistoryRecorder {
    private final MessageBus bus;

    public BusGameHistoryRecorder(MessageBus bus) {
        this.bus = bus;
    }

    @Override
    public void recordMove(String roomId, String notation) {
        bus.publish(RoomBusSubjects.MOVE_HISTORY, MessageType.MOVE_RECORDED,
                new MoveRecordedPayload(roomId, notation, System.currentTimeMillis()));
    }

    @Override
    public void recordGameResult(String roomId, long whiteUserId, long blackUserId, String whiteUsername, String blackUsername,
                                  String winnerColor, String reason, int whiteEloDelta, int blackEloDelta) {
        bus.publish(RoomBusSubjects.GAME_HISTORY, MessageType.GAME_RECORDED,
                new GameRecordedPayload(roomId, whiteUserId, blackUserId, whiteUsername, blackUsername,
                        winnerColor, reason, whiteEloDelta, blackEloDelta, System.currentTimeMillis()));
    }
}
