package shared.protocol.payload;

import lombok.AllArgsConstructor;

/**
 * Published on the global {@code room.allocated} subject (PLAN_split_6_services.md §2/§3.1) to
 * tell a Game Server Shard "this room is yours, start it." In the real 6-way split this is sent by
 * the Game Allocator; at sub-step 3.1 there is no separate Allocator yet, so
 * {@code RemoteGameSessionHandle} (in the monolith) publishes it directly, standing in for that
 * future role - see the class javadoc there.
 */
@AllArgsConstructor
public final class RoomAllocatedPayload {
    public final String roomId;
    public final long whiteId;
    public final String whiteUsername;
    public final int whiteElo;
    public final long blackId;
    public final String blackUsername;
    public final int blackElo;
}
