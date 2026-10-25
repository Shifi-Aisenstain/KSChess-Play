package shared.protocol.payload;

import lombok.AllArgsConstructor;

@AllArgsConstructor
public final class GameOverPayload {
    public final String roomId;
    public final String winnerColor;
    public final String reason;
    public final int whiteEloDelta;
    public final int blackEloDelta;
    public final int whiteEloNew;
    public final int blackEloNew;
    public final String whiteUsername;
    public final String blackUsername;
}
