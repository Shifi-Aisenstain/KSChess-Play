package shared.eventbus.events;

import lombok.AllArgsConstructor;
import models.Piece;
import models.Position;
import shared.eventbus.Event;

/** Published by {@code engine.GameManager} every time a move actually lands on the board. */
@AllArgsConstructor
public final class MoveExecutedEvent implements Event {
    public final String roomId;
    public final Piece movedPiece;
    public final Position from;
    public final Position to;
    public final Piece capturedPiece; // null if nothing was captured
    public final String notation;     // e.g. "Qe2xe5"
    public final int scoreWhite;
    public final int scoreBlack;
    public final String logEntry;     // e.g. "00:05.123|Qe2xe5" - the move-log row, ready for the client's history table
}
