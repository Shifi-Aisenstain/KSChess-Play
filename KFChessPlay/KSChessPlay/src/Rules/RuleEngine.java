package Rules;

import Models.Board;
import Models.Piece;
import Models.Position;
import Controller.MoveCommand;
import Engine.GameManager;

public class RuleEngine {
    private final PieceRules pieceRules = new PieceRules();

    public boolean validateMove(MoveCommand command, Board board, GameManager gameManager) {
        Position src = command.getSource();
        Position dest = command.getDestination();

        // 1. בדיקת גבולות יסודית
        if (!board.isPositionOnBoard(src) || !board.isPositionOnBoard(dest)) return false;

        // 2. האם יש כלי במקור?
        Piece piece = board.getPieceAt(src);
        if (piece == null) return false;

        // 3. האם הכלי כבר תפוס/נע ברגע זה?
        if (gameManager.isPieceBusy(src.getRow(), src.getCol())) return false;

        // 4. בדיקה האם היעד מאוכלס בכלי מאותו הצבע
        Piece destPiece = board.getPieceAt(dest);
        if (destPiece != null && destPiece.getColor() == piece.getColor()) return false;

        // 5. האם היעד נמצא בתוך היעדים המותרים לסוג הכלי הספציפי?
        return pieceRules.getLegalTargets(piece, src, board).contains(dest);
    }
}