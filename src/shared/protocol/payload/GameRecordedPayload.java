package shared.protocol.payload;

import lombok.AllArgsConstructor;

@AllArgsConstructor
public final class GameRecordedPayload {
    public final String roomId;
    public final long whiteUserId;
    public final long blackUserId;
    public final String whiteUsername;
    public final String blackUsername;
    public final String winnerColor;
    public final String reason;
    public final int whiteEloDelta;
    public final int blackEloDelta;
    public final long endedAt;
}
