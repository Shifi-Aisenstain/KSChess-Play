package server.history;

public final class GameSummary {
    public final String roomId;
    public final String whiteUsername;
    public final String blackUsername;
    public final String winnerColor;
    public final String reason;
    public final int whiteEloDelta;
    public final int blackEloDelta;
    public final long endedAt;

    public GameSummary(String roomId, String whiteUsername, String blackUsername, String winnerColor,
                        String reason, int whiteEloDelta, int blackEloDelta, long endedAt) {
        this.roomId = roomId;
        this.whiteUsername = whiteUsername;
        this.blackUsername = blackUsername;
        this.winnerColor = winnerColor;
        this.reason = reason;
        this.whiteEloDelta = whiteEloDelta;
        this.blackEloDelta = blackEloDelta;
        this.endedAt = endedAt;
    }
}
