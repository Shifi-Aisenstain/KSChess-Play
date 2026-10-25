package server.history;

public interface GameHistoryRecorder {

    GameHistoryRecorder NO_OP = new GameHistoryRecorder() {
        @Override public void recordMove(String roomId, String notation) { }
        @Override public void recordGameResult(String roomId, long whiteUserId, long blackUserId,
                                                String whiteUsername, String blackUsername,
                                                String winnerColor, String reason,
                                                int whiteEloDelta, int blackEloDelta) { }
    };

    void recordMove(String roomId, String notation);

    void recordGameResult(String roomId, long whiteUserId, long blackUserId, String whiteUsername, String blackUsername,
                           String winnerColor, String reason, int whiteEloDelta, int blackEloDelta);
}
