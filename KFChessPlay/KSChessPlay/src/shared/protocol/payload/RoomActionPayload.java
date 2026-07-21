package shared.protocol.payload;

import lombok.AllArgsConstructor;

/** Used both for ROOM_JOIN requests (roomId set) and ROOM_CREATE (roomId ignored/null). */
@AllArgsConstructor
public final class RoomActionPayload {
    public final String roomId;
}
