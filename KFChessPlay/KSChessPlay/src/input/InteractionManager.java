package input;

import controller.MoveCommand;
import models.Position;
import models.Piece;
import engine.GameManager;

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

        // לחיצה ראשונה - בחירת כלי
        if (selectedPosition == null) {
            if (clickedPiece != null) {
                selectedPosition = clickedPos;
            }
            return;
        }

        // 🔥 תיקון טסט 9: אם נבחר כלי, ולחצו על כלי אחר מאותו הצבע -> החלף את הבחירה!
        Piece srcPiece = gameManager.getBoard().getPieceAt(selectedPosition);
        if (clickedPiece != null && srcPiece != null && clickedPiece.getColor() == srcPiece.getColor()) {
            selectedPosition = clickedPos; // החלפת הבחירה לכלי החדש
            return;
        }

        // לחיצה שנייה - ביצוע המהלך
        MoveCommand command = new MoveCommand(selectedPosition, clickedPos);
        gameManager.requestMove(command);
        selectedPosition = null;
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