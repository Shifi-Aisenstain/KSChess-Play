package shared.protocol.payload;

import lombok.AllArgsConstructor;

@AllArgsConstructor
public final class RoomRosterPayload {
    public final long userId;
    public final String role;
}
