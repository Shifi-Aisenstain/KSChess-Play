package engine;

import models.Board;
import models.Piece;
import models.Position;
import java.util.List;

public class MoveEvent extends GameEvent {
    private final Position toPosition;

    public MoveEvent(Piece piece, Position fromPosition, Position toPosition, long endTime) {
        super(piece, fromPosition, endTime);
        this.toPosition = toPosition;
    }

    public Position getToPosition() { return toPosition; }

    @Override
    public int getPriority() { return 1; }

    @Override
    public void execute(Board board, List<GameEvent> activeEvents, GameManager gameManager) {
        boolean capturedByJumper = false;

        for (GameEvent event : activeEvents) {
            if (event instanceof JumpEvent
                    && event.getFromPosition().equals(this.toPosition)
                    && event.getPiece().getColor() != this.piece.getColor()) { // רק צבע אויב!
                capturedByJumper = true;
                break;
            }
        }

        if (capturedByJumper) {
            // הכלי נתפס באוויר על ידי אויב - הוא נמחק ממשבצת המקור ולא מגיע ליעד
            gameManager.clearPosition(this.fromPosition);
        } else {
            Piece pieceToPlace = this.piece;
            if (this.piece.getType() == 'P') {
                int promotionRow = (this.piece.getColor() == 'w') ? 0 : board.getLength() - 1;
                if (this.toPosition.getRow() == promotionRow) {
                    pieceToPlace = new Piece(this.piece.getColor(), 'Q');
                }
            }
            // מזיז ליעד ומנקה את המקור בצורה אטומית בסיום התנועה
            gameManager.executeActualMove(this.fromPosition, this.toPosition, pieceToPlace);
        }
    }
}