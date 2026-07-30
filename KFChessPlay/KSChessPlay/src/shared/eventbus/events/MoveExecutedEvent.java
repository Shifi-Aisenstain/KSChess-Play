package shared.eventbus.events;

import lombok.AllArgsConstructor;
import models.Piece;
import models.Position;
import shared.eventbus.Event;

@AllArgsConstructor
public final class MoveExecutedEvent implements Event {
    public final String roomId;
    public final Piece movedPiece;
    public final Position from;
    public final Position to;
    public final Piece capturedPiece;
    public final String notation;
    public final int scoreWhite;
    public final int scoreBlack;
    public final String logEntry;
}
