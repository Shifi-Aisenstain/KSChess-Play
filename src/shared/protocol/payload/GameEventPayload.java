package shared.protocol.payload;

import lombok.AllArgsConstructor;
import shared.eventbus.Event;

@AllArgsConstructor
public final class GameEventPayload implements Event {
    public enum Kind { GAME_STARTED, GAME_ENDED, MOVE_EXECUTED, PIECE_CAPTURED, PIECE_JUMPED }

    public final Kind kind;
    public final char color;
    public final char pieceType;
    public final char capturedType;
    public final String message;
    public final int scoreWhite;
    public final int scoreBlack;
    public final String logEntry;
}
