package shared.protocol.payload;

import lombok.AllArgsConstructor;

/**
 * Used both for ROOM_JOIN (roomId required - the code to join) and ROOM_CREATE (roomId optional - a
 * caller-chosen custom room ID/name; blank or null means the server auto-generates one). Room IDs must
 * be unique; the server rejects a ROOM_CREATE whose custom roomId is already taken.
 */
@AllArgsConstructor
public final class RoomActionPayload {
    public final String roomId;
}
