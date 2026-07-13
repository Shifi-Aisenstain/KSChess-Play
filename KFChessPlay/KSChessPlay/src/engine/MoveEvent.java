package engine;

import models.Board;
import models.Piece;
import models.Position;
import java.util.List;

/**
 * ✅ MoveEvent: Represents a piece in motion (moving from src to dest)
 * 
 * LIFECYCLE:
 * 1. registerMove() creates this event with endTime = now + distance * TIME_PER_CELL_MS
 * 2. While executing: Arbiter.isPieceBusy() returns true (prevents re-selection)
 * 3. At endTime: execute() is called
 * 4. execute() checks for air capture, then moves piece
 * 
 * AIR CAPTURE DETECTION (CR Requirement Part E):
 *   - While this move is executing, check activeEvents for enemy JumpEvent
 *   - If enemy is jumping AT this move's destination → collision!
 *   - This piece is captured (removed from board)
 *   - Never reaches destination
 * 
 * PAWN PROMOTION:
 *   - Handled during execute() phase
 *   - Checks if pawn reached final row
 *   - Converts to Queen if so
 * 
 * PRIORITY: 1 (executes before JumpEvent)
 * This ensures air capture works: arrival checked against active jump
 * 
 * @author Chess Game Architecture
 * @version 1.0 (Movement + Air Capture)
 */
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