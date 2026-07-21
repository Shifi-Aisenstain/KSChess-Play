package shared.eventbus.events;

import models.Piece;
import models.Position;
import shared.eventbus.Event;

public final class PieceJumpedEvent implements Event {
    public final String roomId;
    public final Piece piece;
    public final Position at;

    public PieceJumpedEvent(String roomId, Piece piece, Position at) {
        this.roomId = roomId;
        this.piece = piece;
        this.at = at;
    }
}
