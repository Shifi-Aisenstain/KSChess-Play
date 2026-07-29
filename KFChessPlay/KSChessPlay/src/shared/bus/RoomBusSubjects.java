package shared.bus;

/**
 * Subject-name builders for the room-lifecycle/gameplay traffic listed in
 * PLAN_split_6_services.md §2 - kept in one place so every service (RemoteGameSessionHandle in the
 * monolith today, GameServerShardMain, and later the real WS Gateway/Game Allocator) agrees on the
 * exact string without copy-pasting {@code "room." + roomId + ...} everywhere.
 */
public final class RoomBusSubjects {
    /** Global (not per-room) subject a Shard subscribes to once, to learn about every room it's
     *  been allocated - see PLAN_split_6_services.md §2's {@code room.allocated} row. Since §3.2,
     *  a WS Gateway also needs this (it's how the Gateway learns "the Shard is ready, safe to start
     *  publishing room.&lt;roomId&gt;.commands now" - see {@code server.gateway.GatewayRoomRegistry}). */
    public static final String ROOM_ALLOCATED = "room.allocated";

    /** WS Gateway -> monolith (PLAN_split_6_services.md §3.2, standing in for the Game Allocator -
     *  §3.4): request/reply, reuses {@code MessageType.ROOM_CREATE} for the request and
     *  {@code ROOM_CREATED}/{@code ERROR} for the reply. */
    public static final String ROOM_CREATE = "room.create";

    /** WS Gateway -> monolith: request/reply, reuses {@code MessageType.ROOM_JOIN} for the request
     *  and {@code ROOM_JOINED}/{@code ERROR} for the reply. */
    public static final String ROOM_JOIN = "room.join";

    /** WS Gateway -> monolith: fire-and-forget, reuses {@code MessageType.ROOM_CANCEL}. */
    public static final String ROOM_CANCEL = "room.cancel";

    /** WS Gateway -> monolith (standing in for the Matchmaker - §3.3): fire-and-forget enqueue,
     *  reuses {@code MessageType.PLAY_REQUEST}. */
    public static final String MATCHMAKING_REQUESTS = "matchmaking.requests";

    /** WS Gateway -> monolith: fire-and-forget cancel, reuses {@code MessageType.PLAY_CANCEL}. */
    public static final String MATCHMAKING_CANCEL = "matchmaking.cancel";

    /** monolith -> every WS Gateway instance (broadcast): {@code MessageType.MATCH_FOUND}/
     *  {@code PLAY_NOT_FOUND} wrapping {@code BusMatchFoundPayload}/{@code BusMatchNotFoundPayload}. */
    public static final String MATCHMAKING_RESULTS = "matchmaking.results";

    /**
     * WS Gateway -> monolith, request/reply (§3.2): "which room is this user mid-game in?" -
     * reconnect support, replaces the in-process {@code RoomManager.getActiveRoomForUser} call
     * {@code LoginHandler} used to make directly. Deliberately one shared subject with the userId
     * carried in the request payload ({@code ActiveRoomQueryPayload}) rather than a per-user subject
     * ({@code user.<userId>.active-room}, as PLAN_split_6_services.md §2's table literally names it)
     * - real NATS supports wildcard subscriptions for that, but {@code InMemoryMessageBus} (what this
     * project's tests actually exercise - see {@code MessageBusTest}'s javadoc) explicitly doesn't,
     * and request/reply already isolates concurrent callers via its own per-call reply subject, so
     * there's no correctness reason to need the wildcard here anyway.
     */
    public static final String ACTIVE_ROOM_LOOKUP = "user.active-room";

    private RoomBusSubjects() { }

    /** WS Gateway -> Shard (via {@code server.gateway.GatewayRoomSession} since §3.2 - previously
     *  {@code server.game.RemoteGameSessionHandle} in the monolith, retired in §3.2): SELECT/MOVE/
     *  JUMP/RESIGN/ROOM_ROSTER. */
    public static String commands(String roomId) {
        return "room." + roomId + ".commands";
    }

    /** Shard -> WS Gateway: STATE_UPDATE (per-user), GAME_EVENT, GAME_OVER. Since §3.2 also carries
     *  DISCONNECT_COUNTDOWN, now published directly by whichever WS Gateway instance is running the
     *  disconnect watchdog rather than relayed through the monolith (there's no Room/sinks object in
     *  the monolith to relay through anymore - see {@code server.gateway.DisconnectWatchdog}). */
    public static String updates(String roomId) {
        return "room." + roomId + ".updates";
    }
}
