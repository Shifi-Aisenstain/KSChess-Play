package shared.eventbus.events;

import lombok.AllArgsConstructor;
import models.Piece;
import models.Position;
import shared.eventbus.Event;

@AllArgsConstructor
public final class PieceJumpedEvent implements Event {
    public final String roomId;
    public final Piece piece;
    public final Position at;
}
