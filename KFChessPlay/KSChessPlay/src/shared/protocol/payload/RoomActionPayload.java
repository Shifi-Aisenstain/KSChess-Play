package shared.protocol.payload;

/** Used both for ROOM_JOIN requests (roomId set) and ROOM_CREATE (roomId ignored/null). */
public final class RoomActionPayload {
    public final String roomId;

    public RoomActionPayload(String roomId) {
        this.roomId = roomId;
    }
}
