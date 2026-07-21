package shared.protocol.payload;

import lombok.AllArgsConstructor;

@AllArgsConstructor
public final class MatchFoundPayload {
    public final String roomId;
    public final char assignedColor;
    public final String opponentUsername;
    public final int opponentElo;
}
