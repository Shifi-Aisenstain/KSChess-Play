package test.Test.unit;

import controller.ConsoleIO;
import engine.GameManager;
import input.InteractionManager;
import org.junit.jupiter.api.Test;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import static org.junit.jupiter.api.Assertions.*;

public class ConsoleIOTest {

    @Test
    public void testConsoleIOInitializationAndStart() {
        GameManager gameManager = new GameManager();
        InteractionManager interactionManager = new InteractionManager(gameManager);

        // 1. הגדרת קלט מדומה המייצג לוח קטן ופקודה בסיסית
        String simulatedInput =
                "Board:\n" +
                        "wR .\n" +
                        ". bK\n" +
                        "Commands:\n" +
                        "print board\n";

        // שמירת ה-System.in המקורי
        InputStream originalIn = System.in;

        try {
            // הזרקת הקלט המדומה לתוך המערכת
            System.setIn(new ByteArrayInputStream(simulatedInput.getBytes()));

            // 2. יצירת האובייקט והפעלתו
            ConsoleIO consoleIO = new ConsoleIO(gameManager, interactionManager);
            assertNotNull(consoleIO);

            // הפעלת הזרמת הפקודות (יקרא את הקלט המדומה ויסתיים מיד)
            consoleIO.start();

        } finally {
            // החזרת הזרם המקורי של המערכת למקומו
            System.setIn(originalIn);
        }
    }
}