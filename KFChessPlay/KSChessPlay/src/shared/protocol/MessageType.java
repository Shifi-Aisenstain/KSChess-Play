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
    PONG
}
