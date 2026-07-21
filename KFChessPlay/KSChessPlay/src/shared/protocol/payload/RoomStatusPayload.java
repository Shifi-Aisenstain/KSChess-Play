package shared.protocol.payload;

import lombok.AllArgsConstructor;

@AllArgsConstructor
public final class RoomStatusPayload {
    public final String roomId;
    public final String role;   // "WHITE" | "BLACK" | "SPECTATOR"
}
