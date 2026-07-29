package rules;

import models.Piece;
import models.Position;
import models.Board;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * ✅ PieceRules: All piece-specific movement knowledge lives here
 * - Ray directions (Rook, Bishop, Queen)
 * - Fixed offsets (King, Knight)
 * - Pawn special logic (double move, diagonal capture, promotion)
 *
 * Single Responsibility: All logic about HOW pieces move is here!
 */
public class PieceRules {

    // No piece needs more than 8 direction/offset vectors (Queen/King/Knight), so instead of
    // allocating new int[]{dr, dc} arrays on every getRayDirections/getFixedOffsets call, this is
    // one reusable 8-slot buffer whose cells get overwritten in place per call - zero allocation
    // after construction. view4/view8 are built once here too (List wrappers over that same
    // buffer) and simply reused forever, rather than re-wrapping the buffer on every call.
    //
    // This is safe to mutate because a PieceRules instance is owned by exactly one RuleEngine,
    // which is owned by exactly one GameManager/room - and every access into a given room's
    // GameManager is confined to whichever single thread currently holds that room's
    // GameSession.gameStateLock (see GameSession's threading notes), so two threads never touch
    // the same PieceRules at once. Every caller (RuleEngine, GameManager.getLegalDestinations)
    // also fully reads the returned list before making the next call, so nothing is ever left
    // holding a stale/half-overwritten view.
    private static final int MAX_DIRECTIONS = 8;
    private final int[][] buffer = new int[MAX_DIRECTIONS][2];
    private final List<int[]> view4 = Arrays.asList(buffer).subList(0, 4);
    private final List<int[]> view8 = Arrays.asList(buffer);

    private void put(int index, int dr, int dc) {
        buffer[index][0] = dr;
        buffer[index][1] = dc;
    }

    /**
     * Ray-based pieces (Rook, Bishop, Queen)
     * Returns direction vectors for pieces that move in straight/diagonal lines
     */
    public List<int[]> getRayDirections(Piece piece) {
        if (piece == null) return Collections.emptyList();

        switch (piece.getType()) {
            case 'R': // Rook - orthogonal (up, down, left, right)
                put(0, 1, 0); put(1, -1, 0); put(2, 0, 1); put(3, 0, -1);
                return view4;
            case 'B': // Bishop - diagonal
                put(0, 1, 1); put(1, 1, -1); put(2, -1, 1); put(3, -1, -1);
                return view4;
            case 'Q': // Queen - orthogonal + diagonal (8 directions)
                put(0, 1, 0); put(1, -1, 0); put(2, 0, 1); put(3, 0, -1);
                put(4, 1, 1); put(5, 1, -1); put(6, -1, 1); put(7, -1, -1);
                return view8;
            default:
                return Collections.emptyList();
        }
    }

    /**
     * Fixed-offset pieces (King, Knight)
     * Returns possible destination offsets for pieces with limited/fixed moves
     */
    public List<int[]> getFixedOffsets(Piece piece) {
        if (piece == null) return Collections.emptyList();

        if (piece.getType() == 'K') { // King - 1 step in any of 8 directions
            int i = 0;
            for (int dr = -1; dr <= 1; dr++) {
                for (int dc = -1; dc <= 1; dc++) {
                    if (dr != 0 || dc != 0) {
                        put(i++, dr, dc);
                    }
                }
            }
            return view8;
        } else if (piece.getType() == 'N') { // Knight - L-shaped moves
            put(0, 2, 1); put(1, 2, -1); put(2, -2, 1); put(3, -2, -1);
            put(4, 1, 2); put(5, 1, -2); put(6, -1, 2); put(7, -1, -2);
            return view8;
        }
        return Collections.emptyList();
    }

    /**
     * ✅ Pawn special logic - CENTRALIZED HERE (SRP)
     * Handles:
     * - Single step forward
     * - Double step from starting position
     * - Diagonal capture (handled by RuleEngine target piece check)
     * - Promotion (handled by MoveEvent)
     */
    public MoveValidation validatePawnMove(Piece pawn, Position src, Position dest, Board board) {
        if (pawn.getType() != 'P') {
            return MoveValidation.invalid("Not a pawn");
        }

        int direction = (pawn.getColor() == 'w') ? -1 : 1; // White up (-1), Black down (+1)
        int startRow = (pawn.getColor() == 'w') ? board.getLength() - 2 : 1;

        int rowDiff = dest.getRow() - src.getRow();
        int colDiff = Math.abs(dest.getCol() - src.getCol());

        // ✅ Single step forward (no capture)
        if (rowDiff == direction && colDiff == 0) {
            if (board.getPieceAt(dest) == null) {
                return MoveValidation.valid();
            } else {
                return MoveValidation.invalid("Pawn cannot move to occupied square");
            }
        }

        // ✅ Double step from starting row (no capture, intermediate must be empty)
        if (src.getRow() == startRow && rowDiff == 2 * direction && colDiff == 0) {
            Position intermediate = new Position(src.getRow() + direction, src.getCol());
            if (board.getPieceAt(intermediate) == null && board.getPieceAt(dest) == null) {
                return MoveValidation.valid();
            } else {
                return MoveValidation.invalid("Pawn's path is blocked");
            }
        }

        // ✅ Diagonal capture (one step, only diagonal)
        if (rowDiff == direction && colDiff == 1) {
            Piece target = board.getPieceAt(dest);
            if (target != null && target.getColor() != pawn.getColor()) {
                return MoveValidation.valid(); // ✅ Capture enemy piece
            } else {
                return MoveValidation.invalid("Pawn can only capture diagonally on enemy piece");
            }
        }

        return MoveValidation.invalid("Invalid pawn move");
    }
}