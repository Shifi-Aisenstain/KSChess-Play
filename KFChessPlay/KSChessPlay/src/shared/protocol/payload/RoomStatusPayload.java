package shared.protocol.payload;

public final class RoomStatusPayload {
    public final String roomId;
    public final String role;   // "WHITE" | "BLACK" | "SPECTATOR"

    public RoomStatusPayload(String roomId, String role) {
        this.roomId = roomId;
        this.role = role;
    }
}
