package shared.protocol.payload;

import lombok.AllArgsConstructor;

@AllArgsConstructor
public final class CancelRoomRequestPayload {
    public final long userId;
    public final String roomId;
}
