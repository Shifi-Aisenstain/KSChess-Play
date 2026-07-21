package test.Test.unit;

import models.Board;
import models.GameState;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class GameStateTest {

    @Test
    public void testGameStateInitializationAndTurnSwitching() {
        Board board = new Board(8, 8);
        GameState gameState = new GameState(board);

        // 1. בדיקת ערכי ברירת מחדל לאחר האתחול
        assertNotNull(gameState.getBoard());
        assertFalse(gameState.isGameOver());
        assertEquals("WHITE", gameState.getCurrentTurn());

        // 2. בדיקת מנגנון החלפת התורים
        gameState.switchTurn();
        assertEquals("BLACK", gameState.getCurrentTurn());

        gameState.switchTurn();
        assertEquals("WHITE", gameState.getCurrentTurn());

        // 3. בדיקת שינוי מצב סיום המשחק
        gameState.setGameOver(true);
        assertTrue(gameState.isGameOver());
    }
}