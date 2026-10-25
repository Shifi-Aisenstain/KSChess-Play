package shared.protocol.payload;

import lombok.AllArgsConstructor;

@AllArgsConstructor
public final class BusMatchFoundPayload {
    public final long userId;
    public final String roomId;
    public final char assignedColor;
    public final String opponentUsername;
    public final int opponentElo;
}
