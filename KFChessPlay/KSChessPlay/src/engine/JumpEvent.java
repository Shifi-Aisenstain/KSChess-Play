package engine;

import models.Board;
import models.Piece;
import models.Position;
import java.util.List;

/**
 * ✅ JumpEvent: Represents a piece "jumping" (airborne state)
 * 
 * Behavior:
 * 1. When registered: Piece disappears from board (setPieceAt(pos, null)) [by Arbiter]
 * 2. While jumping (0ms - 1000ms): Position shows empty
 * 3. When endTime reached: execute() is called
 * 4. execute() returns piece to original position
 * 
 * Purpose: Air capture mechanic
 * - White King jumps to block Black Rook's arrival
 * - If Black Rook arrives AFTER jump ends, it lands normally
 * - If Black Rook arrives DURING jump, MoveEvent checks and is blocked
 * 
 * ✅ Timeline Example:
 * - 0ms: jump 50 150 → King airborne, position empty
 * - 500ms: position still empty
 * - 1000ms: JumpEvent.execute() → King returns
 * - Board shows King back at position
 */
public class JumpEvent extends GameEvent {

    public JumpEvent(Piece piece, Position position, long endTime) {
        super(piece, position, endTime);
    }

    @Override
    public int getPriority() {
        return 2; // Execute AFTER moves (Priority 1)
    }

    /**
     * ✅ When jump completes: Return piece to its original position
     * This is why the airborne effect works:
     * - Piece removed during jump (by Arbiter.registerJump)
     * - Piece restored when jump ends (by this execute method)
     */
    @Override
    public void execute(Board board, List<GameEvent> activeEvents, GameManager gameManager) {
        // ✅ Jump complete: piece lands back at its original position
        board.setPieceAt(fromPosition, piece);
    }
}