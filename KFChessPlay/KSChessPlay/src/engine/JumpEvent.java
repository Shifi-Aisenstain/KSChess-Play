package engine;

import models.Board;
import models.Piece;
import models.Position;
import java.util.List;

public class JumpEvent extends GameEvent {

    public JumpEvent(Piece piece, Position position, long endTime) {
        super(piece, position, endTime);
    }

    @Override
    public int getPriority() {
        return 2;
    }

    @Override
    public void execute(Board board, List<GameEvent> activeEvents, GameManager gameManager) {
        board.setPieceAt(fromPosition, piece);
    }
}
