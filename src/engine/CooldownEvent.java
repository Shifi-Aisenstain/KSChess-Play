package engine;

import models.Board;
import models.Piece;
import models.Position;
import java.util.List;

public class CooldownEvent extends GameEvent {

    public enum Type { LONG_REST, SHORT_REST }

    private final Type type;

    public CooldownEvent(Piece piece, Position position, long endTime, Type type) {
        super(piece, position, endTime);
        this.type = type;
    }

    public Type getCooldownType() { return type; }

    @Override
    public int getPriority() { return 3; }

    @Override
    public void execute(Board board, List<GameEvent> activeEvents, GameManager gameManager) {
    }
}
