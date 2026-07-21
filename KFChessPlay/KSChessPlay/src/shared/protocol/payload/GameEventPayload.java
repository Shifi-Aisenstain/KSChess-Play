package shared.protocol.payload;

import shared.eventbus.Event;

/**
 * Lightweight event notification forwarded to clients purely so they can
 * react with sound/animation - as opposed to {@link StateUpdatePayload}
 * which carries the full render-ready board state on every tick.
 */
/** Doubles as a client-local {@link Event} once decoded, so it can be published straight onto the client EventBus - see client.bridge.NetworkGameController. */
public final class GameEventPayload implements Event {
    public enum Kind { GAME_STARTED, GAME_ENDED, MOVE_EXECUTED, PIECE_CAPTURED, PIECE_JUMPED }

    public final Kind kind;
    public final char color;
    public final char pieceType;
    public final char capturedType; // '\0' if nothing was captured
    public final String message;
    public final int scoreWhite;    // only meaningful for MOVE_EXECUTED/PIECE_CAPTURED
    public final int scoreBlack;    // only meaningful for MOVE_EXECUTED/PIECE_CAPTURED
    public final String logEntry;   // "MM:SS.mmm|Notation" row for the move-log table; "" if not a move event

    public GameEventPayload(Kind kind, char color, char pieceType, char capturedType, String message,
                             int scoreWhite, int scoreBlack, String logEntry) {
        this.kind = kind;
        this.color = color;
        this.pieceType = pieceType;
        this.capturedType = capturedType;
        this.message = message;
        this.scoreWhite = scoreWhite;
        this.scoreBlack = scoreBlack;
        this.logEntry = logEntry;
    }
}
