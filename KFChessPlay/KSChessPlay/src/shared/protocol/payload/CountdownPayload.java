package shared.protocol.payload;

public final class CountdownPayload {
    public final String roomId;
    public final char disconnectedColor;
    public final int secondsRemaining;

    public CountdownPayload(String roomId, char disconnectedColor, int secondsRemaining) {
        this.roomId = roomId;
        this.disconnectedColor = disconnectedColor;
        this.secondsRemaining = secondsRemaining;
    }
}
