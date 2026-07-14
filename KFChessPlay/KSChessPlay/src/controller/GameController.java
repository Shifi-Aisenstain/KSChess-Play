package controller;

import engine.GameManager;
import models.Position;

public class GameController {
    private final GameManager gameManager;
    private Position selectedPosition = null;

    public GameController(GameManager gameManager) {
        this.gameManager = gameManager;
    }

    public void handleInput(int row, int col) {
        Position targetPos = new Position(row, col);

        // 1. האם המשתמש כבר בחר כלי (לחיצה שנייה)?
        if (selectedPosition != null) {
            // אם לחץ שוב על אותו כלי -> ביטול בחירה
            if (selectedPosition.equals(targetPos)) {
                selectedPosition = null;
                System.out.println("Selection cancelled.");
            } else {
                // נסיון ביצוע מהלך
                // כאן אנו יוצרים את ה-MoveCommand כפי שהדיזיין דורש
                MoveCommand command = new MoveCommand(selectedPosition, targetPos);
                gameManager.requestMove(command);

                // איפוס בחירה אחרי ניסיון מהלך
                selectedPosition = null;
            }
            return;
        }

        // 2. לחיצה ראשונה: ניסיון לבחור כלי
        // בדיקה מול ה-Board דרך ה-GameManager
        if (gameManager.getBoard().getPieceAt(targetPos) != null) {
            selectedPosition = targetPos;
            System.out.println("Piece selected at: " + row + "," + col);
        }
    }
}