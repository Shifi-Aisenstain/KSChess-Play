package shared.protocol.payload;

import lombok.AllArgsConstructor;

@AllArgsConstructor
public final class MoveRecordedPayload {
    public final String roomId;
    public final String notation;
    public final long playedAt;
}
