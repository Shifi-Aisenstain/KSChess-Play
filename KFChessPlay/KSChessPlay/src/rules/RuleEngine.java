package rules;

import models.Board;
import models.Piece;
import models.Position;
import java.util.List;

/**
 * ✅ STATELESS Rule Validator - CORE CR REQUIREMENT
 * 
 * PURPOSE: Validate ONLY chess rules on a given board state
 * (Real-time checks like isPieceBusy are NOT the responsibility of this class)
 * 
 * STATELESS DESIGN:
 * - No internal Board, GameManager, or event tracking fields
 * - Each validation is a pure function: (board, src, dest) → MoveValidation
 * - All state comes from method parameters, not stored in fields
 * 
 * ❌ VIOLATIONS (MUST NEVER ADD THESE):
 *   private Board board;           // ❌ Would store state
 *   private GameManager manager;   // ❌ Would create coupling
 *   private List<Position> cache;  // ❌ Would hide dependencies
 *   
 * ✅ CORRECT PATTERN (current):
 *   public MoveValidation validateMove(Board board, Position src, Position dest)
 *   - All needed state passed as parameters
 *   - No hidden dependencies
 *   - Each call independent of previous calls
 * 
 * SEPARATION OF CONCERNS:
 * - RuleEngine: "Is this move geometrically valid on this board?"
 * - GameManager: "Is the piece currently busy doing something else?"
 * - These are TWO DIFFERENT QUESTIONS - this class answers only the first
 * 
 * @author Chess Game Architecture
 * @version 1.0 (Stateless)
 */
public class RuleEngine {
    private final PieceRules pieceRules = new PieceRules();

    /**
     * ✅ Stateless: validates ONLY chess rules
     * Real-time checks (isPieceBusy) belong in GameManager!
     */
    public MoveValidation validateMove(Board board, Position source, Position destination) {
        // 1️⃣ בדיקת גבולות
        if (!board.isPositionOnBoard(source)) {
            return MoveValidation.invalid("Source position out of bounds");
        }
        if (!board.isPositionOnBoard(destination)) {
            return MoveValidation.invalid("Destination position out of bounds");
        }

        // 2️⃣ בדיקת קיום כלי במקור
        Piece movingPiece = board.getPieceAt(source);
        if (movingPiece == null) {
            return MoveValidation.invalid("No piece at source position");
        }

        // 3️⃣ בדיקת נחיתה על כלי ידידותי
        Piece targetPiece = board.getPieceAt(destination);
        if (targetPiece != null && targetPiece.getColor() == movingPiece.getColor()) {
            return MoveValidation.invalid("Cannot capture friendly piece");
        }

        // 4️⃣ בדיקת חוקיות גיאומטרית (PieceRules יודע איך כל כלי זז)
        return isTargetGeometricallyValid(movingPiece, source, destination, board);
    }

    /**
     * ✅ Stateless: validates ONLY if a jump is geometrically possible
     * Real-time checks (isPieceBusy, isGameOver) belong in GameManager!
     */
    public MoveValidation validateJump(Board board, Position position) {
        if (!board.isPositionOnBoard(position)) {
            return MoveValidation.invalid("Jump position out of bounds");
        }

        Piece piece = board.getPieceAt(position);
        if (piece == null) {
            return MoveValidation.invalid("No piece to jump");
        }

        // ✅ Jump is geometrically always valid - it's a real-time action
        // (isPieceBusy check belongs in GameManager)
        return MoveValidation.valid();
    }

    /**
     * ✅ Internal: Check if destination is geometrically valid for the piece
     * Delegates to PieceRules for piece-specific logic (SRP)
     */
    private MoveValidation isTargetGeometricallyValid(Piece piece, Position src, Position dest, Board board) {
        char type = piece.getType();

        // 1️⃣ Ray-based pieces (Rook, Bishop, Queen)
        List<int[]> rays = pieceRules.getRayDirections(piece);
        if (!rays.isEmpty()) {
            for (int[] d : rays) {
                int r = src.getRow() + d[0];
                int c = src.getCol() + d[1];
                while (r >= 0 && r < board.getLength() && c >= 0 && c < board.getCols()) {
                    Position current = new Position(r, c);
                    if (current.equals(dest)) {
                        return MoveValidation.valid(); // ✅ Found target
                    }
                    if (board.getPieceAt(current) != null) {
                        break; // Blocking piece found
                    }
                    r += d[0];
                    c += d[1];
                }
            }
            return MoveValidation.invalid("Piece path is blocked or destination not in ray");
        }

        // 2️⃣ Fixed-offset pieces (King, Knight)
        List<int[]> offsets = pieceRules.getFixedOffsets(piece);
        if (!offsets.isEmpty()) {
            for (int[] o : offsets) {
                int targetRow = src.getRow() + o[0];
                int targetCol = src.getCol() + o[1];
                if (targetRow == dest.getRow() && targetCol == dest.getCol()) {
                    return MoveValidation.valid(); // ✅ Valid L-shape or 1-step move
                }
            }
            return MoveValidation.invalid("Destination not in valid move offsets");
        }

        // 3️⃣ Pawn - ✅ Delegated to PieceRules (SRP)
        if (type == 'P') {
            return pieceRules.validatePawnMove(piece, src, dest, board);
        }

        return MoveValidation.invalid("Unknown piece type");
    }
}