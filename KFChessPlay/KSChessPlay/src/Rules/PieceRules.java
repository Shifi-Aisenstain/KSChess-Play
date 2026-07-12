package Rules;

import Models.Board;
import Models.Piece;
import Models.Position;
import java.util.ArrayList;
import java.util.List;

public class PieceRules {

    public List<Position> getLegalTargets(Piece piece, Position source, Board board) {
        List<Position> targets = new ArrayList<>();
        if (piece == null) return targets;

        switch (piece.getType()) {
            case 'R': // צריח
                calculateStraightLines(source, board, targets);
                break;
            case 'B': // רץ
                calculateDiagonalLines(source, board, targets);
                break;
            case 'Q': // מלכה (ישר + אלכסון)
                calculateStraightLines(source, board, targets);
                calculateDiagonalLines(source, board, targets);
                break;
            case 'K': // מלך (1 לכל כיוון)
                calculateKingMoves(source, board, targets);
                break;
            case 'N': // פרש (קפיצות L)
                calculateKnightMoves(source, board, targets);
                break;
            case 'P': // רגלי
                calculatePawnMoves(piece, source, board, targets);
                break;
        }
        return targets;
    }

    private void calculateStraightLines(Position src, Board board, List<Position> targets) {
        int[][] directions = {{1,0}, {-1,0}, {0,1}, {0,-1}};
        extractRayTargets(src, board, targets, directions);
    }

    private void calculateDiagonalLines(Position src, Board board, List<Position> targets) {
        int[][] directions = {{1,1}, {1,-1}, {-1,1}, {-1,-1}};
        extractRayTargets(src, board, targets, directions);
    }

    private void extractRayTargets(Position src, Board board, List<Position> targets, int[][] directions) {
        for (int[] d : directions) {
            int r = src.getRow() + d[0];
            int c = src.getCol() + d[1];
            while (r >= 0 && r < board.getLength() && c >= 0 && c < board.getCols()) {
                Position pos = new Position(r, c);
                targets.add(pos);
                if (board.getPieceAt(pos) != null) {
                    break; // נחסם (ה-RuleEngine יבדוק אם זה אויב או ידיד)
                }
                r += d[0];
                c += d[1];
            }
        }
    }

    private void calculateKingMoves(Position src, Board board, List<Position> targets) {
        int[] steps = {-1, 0, 1};
        for (int dr : steps) {
            for (int dc : steps) {
                if (dr == 0 && dc == 0) continue;
                Position pos = new Position(src.getRow() + dr, src.getCol() + dc);
                if (board.isPositionOnBoard(pos)) {
                    targets.add(pos);
                }
            }
        }
    }

    private void calculateKnightMoves(Position src, Board board, List<Position> targets) {
        int[][] moves = {
                {2, 1}, {2, -1}, {-2, 1}, {-2, -1},
                {1, 2}, {1, -2}, {-1, 2}, {-1, -2}
        };
        for (int[] m : moves) {
            Position pos = new Position(src.getRow() + m[0], src.getCol() + m[1]);
            if (board.isPositionOnBoard(pos)) {
                targets.add(pos); // פרש מדלג מעל כלים, לכן רק מוסיפים את משבצת הנחיתה
            }
        }
    }

    private void calculatePawnMoves(Piece piece, Position src, Board board, List<Position> targets) {
        int direction = (piece.getColor() == 'w') ? -1 : 1;

        Position oneStep = new Position(src.getRow() + direction, src.getCol());
        if (board.isPositionOnBoard(oneStep) && board.getPieceAt(oneStep) == null) {
            targets.add(oneStep);

            int startRow = (piece.getColor() == 'w') ? board.getLength() - 2 : 1;
            if (src.getRow() == startRow) {
                Position twoSteps = new Position(src.getRow() + (2 * direction), src.getCol());
                if (board.isPositionOnBoard(twoSteps) && board.getPieceAt(twoSteps) == null) {
                    targets.add(twoSteps);
                }
            }
        }
    }
}