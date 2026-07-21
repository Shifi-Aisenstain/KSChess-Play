package shared.protocol.payload;

public final class GameOverPayload {
    public final String roomId;
    public final String winnerColor;   // "WHITE" | "BLACK" | null (draw/abort)
    public final String reason;        // "checkmate" | "resignation" | "disconnect_timeout"
    public final int whiteEloDelta;
    public final int blackEloDelta;
    public final int whiteEloNew;
    public final int blackEloNew;
    public final String whiteUsername;
    public final String blackUsername;

    public GameOverPayload(String roomId, String winnerColor, String reason,
                            int whiteEloDelta, int blackEloDelta, int whiteEloNew, int blackEloNew,
                            String whiteUsername, String blackUsername) {
        this.roomId = roomId;
        this.winnerColor = winnerColor;
        this.reason = reason;
        this.whiteEloDelta = whiteEloDelta;
        this.blackEloDelta = blackEloDelta;
        this.whiteEloNew = whiteEloNew;
        this.blackEloNew = blackEloNew;
        this.whiteUsername = whiteUsername;
        this.blackUsername = blackUsername;
    }
}
