package shared.protocol;

/**
 * Every message that can cross the Client <-> Server WebSocket boundary.
 * Keeping this as a closed enum (rather than free-form strings scattered
 * around the codebase) is what lets {@link MessageDispatcherKeys} stay
 * exhaustive and lets the compiler catch typos instead of a runtime NPE.
 */
public enum MessageType {
    // Client -> Server
    LOGIN,
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

    // Internal bus only, PLAN_split_6_services.md §3.2 (WS Gateway <-> monolith room/matchmaking RPC).
    // ROOM_CREATE/ROOM_JOIN/ROOM_CANCEL/PLAY_REQUEST/PLAY_CANCEL/ROOM_CREATED/ROOM_JOINED/MATCH_FOUND/
    // PLAY_NOT_FOUND/ERROR above are all reused as-is for the matching bus request/reply - see
    // RoomBusSubjects and server.rpc.MonolithBusGateway.
    ACTIVE_ROOM_LOOKUP // envelope type for both request and reply on user.<userId>.active-room
}
