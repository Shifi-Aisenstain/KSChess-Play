package shared.protocol.payload;

import lombok.AllArgsConstructor;

/**
 * Published on the global {@code room.allocated} subject (PLAN_split_6_services.md §2/§3.1) to
 * tell a Game Server Shard "this room is yours, start it." Broadcast to every Shard instance (and
 * every WS Gateway instance, which just needs to know "allocation happened", not to which shard -
 * see {@code server.gateway.GatewayRoomRegistry}); {@link #shardId} is how each Shard tells whether a
 * given allocation is actually meant for it (PLAN_split_6_services.md §3.4's round-robin shard
 * selection - see {@code server.allocator.ShardSelector}) rather than one of its siblings, without
 * needing a separate per-shard subject.
 */
@AllArgsConstructor
public final class RoomAllocatedPayload {
    public final String roomId;
    public final String shardId;
    public final long whiteId;
    public final String whiteUsername;
    public final int whiteElo;
    public final long blackId;
    public final String blackUsername;
    public final int blackElo;
}
