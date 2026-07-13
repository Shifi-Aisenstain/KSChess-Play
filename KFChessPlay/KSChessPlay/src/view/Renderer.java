package view;

import models.GameState;
import io.BoardPrinter;

public class Renderer {
    private BoardPrinter boardPrinter;

    public Renderer() {
        this.boardPrinter = new BoardPrinter();
    }

    /**
     * מציג את כל מסך המשחק למשתמש (לוח + הודעות מערכת)
     */
    public void render(GameState gameState) {
        // 1. מדפיס את הלוח באמצעות ה-Printer
        boardPrinter.printBoard(gameState.getBoard());

        // 2. מציג מידע נוסף על מצב המשחק
        System.out.println("תור השחקן: " + gameState.getCurrentTurn());

        if (gameState.isGameOver()) {
            System.out.println("המשחק הסתיים!");
        }
    }
}