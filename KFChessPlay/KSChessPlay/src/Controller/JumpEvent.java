package Controller;

import Models.Board;
import java.util.List;

public class JumpEvent extends GameEvent {
    public JumpEvent(Models.Piece piece, int row, int col, long endTime) {
        super(piece, row, col, endTime);
    }

    @Override
    public void execute(Board board, List<GameEvent> activeEvents, GameManager gameManager) {
        activeEvents.remove(this);
    }
}