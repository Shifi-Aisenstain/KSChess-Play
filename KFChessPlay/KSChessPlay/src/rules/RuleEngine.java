package rules;

import models.Board;
import models.Piece;
import models.Position;
import java.util.List;

/**
 * ✅ Stateless Rule Validator
 * אחראי אך ורק על חוקי השחמט.
 */
public class RuleEngine {
    private final PieceRules pieceRules = new PieceRules();

    public MoveValidation validateMove(Board board, Position source, Position destination) {
        // 1️⃣ בדיקת גבולות הלוח
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

        // 3️⃣ בדיקת נחיתה על כלי ידידותי (מונע "הריגה עצמית")
        Piece targetPiece = board.getPieceAt(destination);
        if (targetPiece != null && targetPiece.getColor() == movingPiece.getColor()) {
            return MoveValidation.invalid("Cannot capture friendly piece");
        }

        // 4️⃣ בדיקת חוקיות גיאומטרית (האם הכלי יכול לזוז לשם?)
        return isTargetGeometricallyValid(movingPiece, source, destination, board);
    }

    public MoveValidation validateJump(Board board, Position position) {
        if (!board.isPositionOnBoard(position)) {
            return MoveValidation.invalid("Jump position out of bounds");
        }

        Piece piece = board.getPieceAt(position);
        if (piece == null) {
            return MoveValidation.invalid("No piece to jump");
        }
        return MoveValidation.valid();
    }

    private MoveValidation isTargetGeometricallyValid(Piece piece, Position src, Position dest, Board board) {
        char type = piece.getType();

        // 1️⃣ כלים שזזים בקווים ישרים (Rook, Bishop, Queen)
        List<int[]> rays = pieceRules.getRayDirections(piece);
        if (!rays.isEmpty()) {
            for (int[] d : rays) {
                int r = src.getRow() + d[0];
                int c = src.getCol() + d[1];
                while (r >= 0 && r < board.getLength() && c >= 0 && c < board.getCols()) {
                    Position current = new Position(r, c);
                    if (current.equals(dest)) {
                        return MoveValidation.valid();
                    }
                    if (board.getPieceAt(current) != null) {
                        break; // נתקע בכלי בדרך
                    }
                    r += d[0];
                    c += d[1];
                }
            }
            return MoveValidation.invalid("Piece path is blocked or destination not in ray");
        }

        // 2️⃣ כלים עם מהלכים קבועים (King, Knight)
        List<int[]> offsets = pieceRules.getFixedOffsets(piece);
        if (!offsets.isEmpty()) {
            for (int[] o : offsets) {
                int targetRow = src.getRow() + o[0];
                int targetCol = src.getCol() + o[1];
                if (targetRow == dest.getRow() && targetCol == dest.getCol()) {
                    return MoveValidation.valid();
                }
            }
            return MoveValidation.invalid("Destination not in valid move offsets");
        }

        // 3️⃣ חייל (Pawn)
        if (type == 'P') {
            return pieceRules.validatePawnMove(piece, src, dest, board);
        }

        return MoveValidation.invalid("Unknown piece type");
    }
}