package shared.protocol.payload;

import lombok.AllArgsConstructor;

/** Bus request on {@code room.cancel} (PLAN_split_6_services.md §3.2): WS Gateway -> monolith,
 *  fire-and-forget (mirrors the original in-process {@code RoomManager.abandonRoom} call, which
 *  never replied to the client either). */
@AllArgsConstructor
public final class CancelRoomRequestPayload {
    public final long userId;
    public final String roomId;
}
