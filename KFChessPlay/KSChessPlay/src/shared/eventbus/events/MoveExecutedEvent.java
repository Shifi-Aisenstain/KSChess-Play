package shared.eventbus.events;

import models.Piece;
import models.Position;
import shared.eventbus.Event;

/** Published by {@code engine.GameManager} every time a move actually lands on the board. */
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

    public MoveExecutedEvent(String roomId, Piece movedPiece, Position from, Position to,
                              Piece capturedPiece, String notation, int scoreWhite, int scoreBlack,
                              String logEntry) {
        this.roomId = roomId;
        this.movedPiece = movedPiece;
        this.from = from;
        this.to = to;
        this.capturedPiece = capturedPiece;
        this.notation = notation;
        this.scoreWhite = scoreWhite;
        this.scoreBlack = scoreBlack;
        this.logEntry = logEntry;
    }
}
