package shared.protocol.payload;

import lombok.AllArgsConstructor;

@AllArgsConstructor
public final class CountdownPayload {
    public final String roomId;
    public final char disconnectedColor;
    public final int secondsRemaining;
}
