package Controller;

import Models.Position;
import Models.Piece;
import Engine.GameManager;

public class InteractionManager {
    private final GameManager gameManager;
    private Position selectedPosition = null;

    public InteractionManager(GameManager gameManager) {
        this.gameManager = gameManager;
    }

    public void handleClick(int x, int y) {
        Position clickedPos = CoordinateParser.parseClick(
                x, y,
                gameManager.getBoard().getLength(),
                gameManager.getBoard().getCols()
        );

        if (clickedPos == null) {
            selectedPosition = null;
            return;
        }

        Piece clickedPiece = gameManager.getBoard().getPieceAt(clickedPos);

        if (selectedPosition == null) {
            // לחיצה ראשונה - בחירת כלי
            if (clickedPiece != null) {
                selectedPosition = clickedPos;
            }
        } else {
            Piece selectedPiece = gameManager.getBoard().getPieceAt(selectedPosition);

            // 🔥 תיקון קריטי: מחליפים בחירה רק אם לחצנו על כלי אחר מאותו הצבע של השחקן הנוכחי!
            if (clickedPiece != null && selectedPiece != null && clickedPiece.getColor() == selectedPiece.getColor() && !clickedPos.equals(selectedPosition)) {
                selectedPosition = clickedPos;
            } else {
                // אם זה יעד ריק או כלי אויב - מדובר בניסיון מהלך/אכילה
                MoveCommand command = new MoveCommand(selectedPosition, clickedPos);
                gameManager.requestMove(command);
                selectedPosition = null; // איפוס הבחירה לאחר המהלך
            }
        }
    }

    public void handleJump(int x, int y) {
        Position clickedPos = CoordinateParser.parseClick(
                x, y,
                gameManager.getBoard().getLength(),
                gameManager.getBoard().getCols()
        );
        if (clickedPos != null) {
            gameManager.requestJump(clickedPos);
        }
        selectedPosition = null;
    }
}