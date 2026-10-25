package shared.protocol.payload;

import lombok.AllArgsConstructor;

@AllArgsConstructor
public final class CreateRoomRequestPayload {
    public final long userId;
    public final String username;
    public final int elo;
    public final String desiredRoomId;
}
