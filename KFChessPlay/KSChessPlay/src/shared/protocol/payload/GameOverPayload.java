package shared.protocol.payload;

import lombok.AllArgsConstructor;

@AllArgsConstructor
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
}
