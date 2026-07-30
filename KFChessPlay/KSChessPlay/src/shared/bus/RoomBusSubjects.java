package shared.bus;

/**
 * Subject-name builders for the room-lifecycle/gameplay traffic listed in
 * PLAN_split_6_services.md §2 - kept in one place so every service (the Game Server Shard, the
 * Game Allocator, the WS Gateway, the Matchmaker) agrees on the exact string without copy-pasting
 * {@code "room." + roomId + ...} everywhere.
 */
public final class RoomBusSubjects {
    /** Global (not per-room) subject a Shard subscribes to once, to learn about every room it's
     *  been allocated - see PLAN_split_6_services.md §2's {@code room.allocated} row. A WS Gateway
     *  also needs this (it's how the Gateway learns "the Shard is ready, safe to start publishing
     *  room.&lt;roomId&gt;.commands now" - see {@code server.gateway.GatewayRoomRegistry}). Broadcast
     *  to every Shard instance; each one filters by {@code RoomAllocatedPayload#shardId} to tell
     *  whether a given allocation is actually meant for it (§3.4's round-robin shard selection - see
     *  {@code server.allocator.ShardSelector}). */
    public static final String ROOM_ALLOCATED = "room.allocated";

    /** WS Gateway -> Game Allocator (PLAN_split_6_services.md §3.4): request/reply, reuses
     *  {@code MessageType.ROOM_CREATE} for the request and {@code ROOM_CREATED}/{@code ERROR} for
     *  the reply. */
    public static final String ROOM_CREATE = "room.create";

    /** WS Gateway -> Game Allocator: request/reply, reuses {@code MessageType.ROOM_JOIN} for the
     *  request and {@code ROOM_JOINED}/{@code ERROR} for the reply. */
    public static final String ROOM_JOIN = "room.join";

    /** WS Gateway -> Game Allocator: fire-and-forget, reuses {@code MessageType.ROOM_CANCEL}. */
    public static final String ROOM_CANCEL = "room.cancel";

    /** WS Gateway -> Matchmaker (PLAN_split_6_services.md §3.3): fire-and-forget enqueue, reuses
     *  {@code MessageType.PLAY_REQUEST}. */
    public static final String MATCHMAKING_REQUESTS = "matchmaking.requests";

    /** WS Gateway -> Matchmaker: fire-and-forget cancel, reuses {@code MessageType.PLAY_CANCEL}. */
    public static final String MATCHMAKING_CANCEL = "matchmaking.cancel";

    /** Matchmaker -> every WS Gateway instance (broadcast): {@code MessageType.MATCH_FOUND}/
     *  {@code PLAY_NOT_FOUND} wrapping {@code BusMatchFoundPayload}/{@code BusMatchNotFoundPayload}. */
    public static final String MATCHMAKING_RESULTS = "matchmaking.results";

    /**
     * WS Gateway -> Game Allocator, request/reply (§3.2/§3.4): "which room is this user mid-game
     * in?" - reconnect support, answered by {@code RoomManager.getActiveRoomForUser} (see
     * {@code server.allocator.ActiveRoomRegistry}, Server_Design.md step 4). Deliberately one shared
     * subject with the userId carried in the request payload ({@code ActiveRoomQueryPayload}) rather
     * than a per-user subject ({@code user.<userId>.active-room}, as PLAN_split_6_services.md §2's
     * table literally names it) - real NATS supports wildcard subscriptions for that, but
     * {@code InMemoryMessageBus} (what this project's tests actually exercise - see
     * {@code MessageBusTest}'s javadoc) explicitly doesn't, and request/reply already isolates
     * concurrent callers via its own per-call reply subject, so there's no correctness reason to
     * need the wildcard here anyway.
     */
    public static final String ACTIVE_ROOM_LOOKUP = "user.active-room";

    /** Game Server Shard -> Elo writer (Server_Design.md step 5): fire-and-forget, reuses
     *  {@code MessageType.ELO_UPDATE_REQUESTED} wrapping {@code EloUpdateRequestedPayload} - see
     *  {@code server.rating.EloWriter}/{@code server.persistence.BusEloWriter} and the batched
     *  consumer, {@code server.persistence.EloWriterMain}. */
    public static final String ELO_UPDATES = "persistence.elo-updates";

    private RoomBusSubjects() { }

    /** WS Gateway -> Shard (via {@code server.gateway.GatewayRoomSession}): SELECT/MOVE/JUMP/RESIGN/
     *  ROOM_ROSTER. */
    public static String commands(String roomId) {
        return "room." + roomId + ".commands";
    }

    /** Shard -> WS Gateway: STATE_UPDATE (per-user), GAME_EVENT, GAME_OVER. Also carries
     *  DISCONNECT_COUNTDOWN, published directly by whichever WS Gateway instance is running the
     *  disconnect watchdog for that room (see {@code server.gateway.DisconnectWatchdog}). */
    public static String updates(String roomId) {
        return "room." + roomId + ".updates";
    }
}
