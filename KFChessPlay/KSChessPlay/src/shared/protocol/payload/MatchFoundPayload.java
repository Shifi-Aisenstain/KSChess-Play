package shared.protocol.payload;

public final class MatchFoundPayload {
    public final String roomId;
    public final char assignedColor;
    public final String opponentUsername;
    public final int opponentElo;

    public MatchFoundPayload(String roomId, char assignedColor, String opponentUsername, int opponentElo) {
        this.roomId = roomId;
        this.assignedColor = assignedColor;
        this.opponentUsername = opponentUsername;
        this.opponentElo = opponentElo;
    }
}
