package test.Test.unit;

import engine.GameManager;
import models.Position;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class ControllerTest {

    @Test
    public void testControllerInitialization() {
        GameManager gameManager = new GameManager();
        gameManager.initializeBoard(4, 4);

        // בדיקה שמנהל המשחק מגיב ואינו ריק
        assertNotNull(gameManager.getBoard());
        assertFalse(gameManager.isPieceBusy(0, 0));
    }
}