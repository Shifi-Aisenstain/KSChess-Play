package shared.protocol;

/**
 * Every message that can cross the Client <-> Server WebSocket boundary.
 * Keeping this as a closed enum (rather than free-form strings scattered
 * around the codebase) is what lets {@link MessageDispatcherKeys} stay
 * exhaustive and lets the compiler catch typos instead of a runtime NPE.
 */
public enum MessageType {
    // Client -> Server
    // Since PLAN_split_6_services.md §3.5, authentication itself happens over REST (POST /login on
    // the API Gateway, see server.apigateway.ApiGatewayMain) before the socket is even opened - LOGIN
    // is gone from this protocol. IDENTIFY carries the already-authenticated identity (IdentifyPayload)
    // so the WS Gateway can attach a User to this PlayerSession without re-checking a password.
    IDENTIFY,
    PLAY_REQUEST,
    PLAY_CANCEL,
    ROOM_CREATE,
    ROOM_JOIN,
    ROOM_CANCEL,
    SELECT,
    MOVE,
    JUMP,
    RESIGN,
    PING,

    // Server -> Client
    LOGIN_RESULT,
    PLAY_WAITING,
    MATCH_FOUND,
    PLAY_NOT_FOUND,
    ROOM_CREATED,
    ROOM_JOINED,
    STATE_UPDATE,
    GAME_EVENT,
    DISCONNECT_COUNTDOWN,
    GAME_OVER,
    ERROR,
    PONG,

    // Internal service-to-service bus only (Server_Design.md §3.3 / PLAN_split_6_services.md §2) -
    // never sent to a client, so DispatcherFactory never registers a handler for these.
    ROOM_ALLOCATED,  // Allocator (today: RoomManager standing in for it) -> Shard: "room X is yours, start it"
    ROOM_COMMAND,    // WS Gateway (today: RemoteGameSessionHandle) -> Shard, on room.<roomId>.commands: wraps a RoutedCommandPayload
    ROOM_UPDATE,     // Shard -> WS Gateway, on room.<roomId>.updates: wraps a RoutedUpdatePayload
    ROOM_ROSTER,     // inner type of a ROOM_COMMAND carrying a RoomRosterPayload (member join/reconnect)
    COMMAND_ACK,     // Shard's reply to a request/reply MOVE/JUMP ROOM_COMMAND: wraps a CommandAckPayload

    // Internal bus only, PLAN_split_6_services.md §3.2/§3.3/§3.4 (WS Gateway <-> Game Allocator/
    // Matchmaker RPC). ROOM_CREATE/ROOM_JOIN/ROOM_CANCEL/PLAY_REQUEST/PLAY_CANCEL/ROOM_CREATED/
    // ROOM_JOINED/MATCH_FOUND/PLAY_NOT_FOUND/ERROR above are all reused as-is for the matching bus
    // request/reply - see RoomBusSubjects, server.gateway.RoomLifecycleClient/MatchmakingClient, and
    // their answering server.allocator.GameAllocatorRuntime/server.matchmaking.MatchmakerRuntime.
    ACTIVE_ROOM_LOOKUP, // envelope type for both request and reply on user.<userId>.active-room

    // Internal bus only, Server_Design.md step 5: a Game Server Shard publishes this instead of
    // writing Elo synchronously through UserRepository - see server.rating.EloWriter/
    // server.persistence.BusEloWriter and the batched consumer, server.persistence.EloWriterMain.
    ELO_UPDATE_REQUESTED
}
