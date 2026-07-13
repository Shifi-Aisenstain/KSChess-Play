import engine.GameManager;
import input.InteractionManager;
import controller.ConsoleIO;

public class Main {
    public static void main(String[] args) {
        // 1. יצירת מנוע המשחק (משתמש בבנאי הריק, ללא שגיאות)
        GameManager gameManager = new GameManager();

        // 2. יצירת מנהל האינטראקציות
        InteractionManager interactionManager = new InteractionManager(gameManager);

        // 3. יצירת שכבת הקלט/פלט והזרקת המנהלים
        ConsoleIO consoleIO = new ConsoleIO(gameManager, interactionManager);

        // 4. התנעה והרצת המשחק
        consoleIO.start();
    }
}