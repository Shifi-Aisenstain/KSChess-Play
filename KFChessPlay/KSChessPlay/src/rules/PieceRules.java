package rules;

import models.Piece;
import models.Position;
import models.Board;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * ✅ PieceRules: CENTRALIZED piece movement logic (SRP - Single Responsibility Principle)
 * 
 * This class owns ALL knowledge about how chess pieces move:
 * - Ray-based pieces: Rook (orthogonal), Bishop (diagonal), Queen (both)
 * - Fixed-offset pieces: King (1 step any direction), Knight (L-shape)
 * - Pawn (special logic): Single step, double from start, diagonal capture
 * 
 * WHY CENTRALIZED HERE?
 * - Before: Pawn logic was scattered in RuleEngine AND Piece class → maintenance nightmare
 * - Now: All piece logic in ONE place → change pawn rules once, fixes everywhere
 * - Enables testing piece movement independently from board state
 * 
 * CRITICAL SRP RULES:
 * ❌ WRONG: Adding piece logic to RuleEngine
 * ❌ WRONG: Adding piece logic to Piece class
 * ✅ CORRECT: All piece logic lives HERE
 * 
 * @author Chess Game Architecture
 * @version 1.0 (SRP Compliant)
 */
public class PieceRules {

    /**
     * ✅ Ray-based pieces (Rook, Bishop, Queen)
     * Returns direction vectors for pieces that move in straight/diagonal lines
     */
    public List<int[]> getRayDirections(Piece piece) {
        if (piece == null) return Collections.emptyList();

        switch (piece.getType()) {
            case 'R': // Rook - orthogonal (up, down, left, right)
                return Arrays.asList(
                    new int[]{1, 0}, new int[]{-1, 0}, 
                    new int[]{0, 1}, new int[]{0, -1}
                );
            case 'B': // Bishop - diagonal
                return Arrays.asList(
                    new int[]{1, 1}, new int[]{1, -1}, 
                    new int[]{-1, 1}, new int[]{-1, -1}
                );
            case 'Q': // Queen - orthogonal + diagonal (8 directions)
                return Arrays.asList(
                    new int[]{1, 0}, new int[]{-1, 0}, 
                    new int[]{0, 1}, new int[]{0, -1},
                    new int[]{1, 1}, new int[]{1, -1}, 
                    new int[]{-1, 1}, new int[]{-1, -1}
                );
            default:
                return Collections.emptyList();
        }
    }

    /**
     * ✅ Fixed-offset pieces (King, Knight)
     * Returns possible destination offsets for pieces with limited/fixed moves
     */
    public List<int[]> getFixedOffsets(Piece piece) {
        if (piece == null) return Collections.emptyList();
        List<int[]> offsets = new ArrayList<>();

        if (piece.getType() == 'K') { // King - 1 step in any of 8 directions
            for (int dr = -1; dr <= 1; dr++) {
                for (int dc = -1; dc <= 1; dc++) {
                    if (dr != 0 || dc != 0) {
                        offsets.add(new int[]{dr, dc});
                    }
                }
            }
        } else if (piece.getType() == 'N') { // Knight - L-shaped moves
            offsets.addAll(Arrays.asList(
                new int[]{2, 1}, new int[]{2, -1}, 
                new int[]{-2, 1}, new int[]{-2, -1},
                new int[]{1, 2}, new int[]{1, -2}, 
                new int[]{-1, 2}, new int[]{-1, -2}
            ));
        }
        return offsets;
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