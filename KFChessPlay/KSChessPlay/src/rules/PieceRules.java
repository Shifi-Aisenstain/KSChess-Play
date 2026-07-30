package rules;

import models.Piece;
import models.Position;
import models.Board;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class PieceRules {

    private static final int MAX_DIRECTIONS = 8;
    private final int[][] buffer = new int[MAX_DIRECTIONS][2];
    private final List<int[]> view4 = Arrays.asList(buffer).subList(0, 4);
    private final List<int[]> view8 = Arrays.asList(buffer);

    private void put(int index, int dr, int dc) {
        buffer[index][0] = dr;
        buffer[index][1] = dc;
    }

    public List<int[]> getRayDirections(Piece piece) {
        if (piece == null) return Collections.emptyList();

        switch (piece.getType()) {
            case 'R':
                put(0, 1, 0); put(1, -1, 0); put(2, 0, 1); put(3, 0, -1);
                return view4;
            case 'B':
                put(0, 1, 1); put(1, 1, -1); put(2, -1, 1); put(3, -1, -1);
                return view4;
            case 'Q':
                put(0, 1, 0); put(1, -1, 0); put(2, 0, 1); put(3, 0, -1);
                put(4, 1, 1); put(5, 1, -1); put(6, -1, 1); put(7, -1, -1);
                return view8;
            default:
                return Collections.emptyList();
        }
    }

    public List<int[]> getFixedOffsets(Piece piece) {
        if (piece == null) return Collections.emptyList();

        if (piece.getType() == 'K') {
            int i = 0;
            for (int dr = -1; dr <= 1; dr++) {
                for (int dc = -1; dc <= 1; dc++) {
                    if (dr != 0 || dc != 0) {
                        put(i++, dr, dc);
                    }
                }
            }
            return view8;
        } else if (piece.getType() == 'N') {
            put(0, 2, 1); put(1, 2, -1); put(2, -2, 1); put(3, -2, -1);
            put(4, 1, 2); put(5, 1, -2); put(6, -1, 2); put(7, -1, -2);
            return view8;
        }
        return Collections.emptyList();
    }

    public MoveValidation validatePawnMove(Piece pawn, Position src, Position dest, Board board) {
        if (pawn.getType() != 'P') {
            return MoveValidation.invalid("Not a pawn");
        }

        int direction = (pawn.getColor() == 'w') ? -1 : 1;
        int startRow = (pawn.getColor() == 'w') ? board.getLength() - 2 : 1;

        int rowDiff = dest.getRow() - src.getRow();
        int colDiff = Math.abs(dest.getCol() - src.getCol());

        if (rowDiff == direction && colDiff == 0) {
            if (board.getPieceAt(dest) == null) {
                return MoveValidation.valid();
            } else {
                return MoveValidation.invalid("Pawn cannot move to occupied square");
            }
        }

        if (src.getRow() == startRow && rowDiff == 2 * direction && colDiff == 0) {
            Position intermediate = new Position(src.getRow() + direction, src.getCol());
            if (board.getPieceAt(intermediate) == null && board.getPieceAt(dest) == null) {
                return MoveValidation.valid();
            } else {
                return MoveValidation.invalid("Pawn's path is blocked");
            }
        }

        if (rowDiff == direction && colDiff == 1) {
            Piece target = board.getPieceAt(dest);
            if (target != null && target.getColor() != pawn.getColor()) {
                return MoveValidation.valid();
            } else {
                return MoveValidation.invalid("Pawn can only capture diagonally on enemy piece");
            }
        }

        return MoveValidation.invalid("Invalid pawn move");
    }
}
