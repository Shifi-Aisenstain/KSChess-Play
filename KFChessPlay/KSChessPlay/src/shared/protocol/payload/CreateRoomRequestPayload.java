package shared.protocol.payload;

import lombok.AllArgsConstructor;

/** Bus request on {@code room.create} (PLAN_split_6_services.md §3.2): WS Gateway -> monolith
 *  (standing in for the not-yet-split Game Allocator, per §3.4). Mirrors the client-facing
 *  ROOM_CREATE/{@link RoomActionPayload}, plus the caller identity a bus request needs since there's
 *  no socket session to read it from on the other end. */
@AllArgsConstructor
public final class CreateRoomRequestPayload {
    public final long userId;
    public final String username;
    public final int elo;
    public final String desiredRoomId;
}
