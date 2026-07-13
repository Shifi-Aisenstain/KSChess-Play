package engine;

import models.Board;
import models.Piece;
import models.Position;
import java.util.List;

/**
 * ✅ JumpEvent: Represents airborne state (piece temporarily vanishes and reappears)
 * 
 * LIFECYCLE:
 * 1. registerJump() creates this event with duration = JUMP_DURATION_MS
 * 2. Piece stays visible on board (protects its square from friendly pieces)
 * 3. At endTime: execute() is called
 * 4. execute() returns piece to its original position
 * 
 * JUMP DOES NOT PERMANENTLY REMOVE PIECE (CR Requirement Part E):
 *   - OLD BUG: Jumping piece was removed from board with setPieceAt(pos, null)
 *   - Problem: Friendly pieces could land on its square (illegal!)
 *   - Solution: Keep piece visible, it acts as a "ghost" that blocks friendly pieces
 * 
 * AIR CAPTURE MECHANIC:
 *   Purpose: Jumping piece can intercept arriving enemy piece
 *   
 *   Example:
 *   - White King at (1, 0), jumps
 *   - Black Rook at (3, 0), moves left toward (1, 0)
 *   
 *   Timeline:
 *   - 0ms: King jumps (stays visible)
 *   - 1000ms: Jump ends, King returns to (1, 0)
 *   - 2000ms: Rook arrives at (1, 0) → captures King!
 *   
 *   If instead:
 *   - 0ms: King jumps
 *   - 1000ms: Rook arrives at (1, 0) BEFORE jump ends
 *   - Result: Rook is captured by King (air capture!)
 *   - Rook never lands
 * 
 * PRIORITY: 2 (executes AFTER MoveEvent)
 * This ensures moves are checked for air capture before jumps complete
 * 
 * @author Chess Game Architecture
 * @version 1.0 (Air Capture Mechanic)
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