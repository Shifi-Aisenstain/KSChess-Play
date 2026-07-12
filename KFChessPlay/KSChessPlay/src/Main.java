import Engine.GameManager;             // 🔥 עודכן מ-Controller.GameManager ל-Engine.GameManager
import Controller.InteractionManager;
import Controller.ConsoleIO;           // 🔥 עודכן מ-IO.ConsoleIO ל-Controller.ConsoleIO

public class Main {
    public static void main(String[] args) {
        // 1. יצירת מנוע המשחק (המחזיק את הלוח, האירועים והשעון)
        GameManager gameManager = new GameManager();

        // 2. יצירת מנהל האינטראקציות (המטפל בקליקים ובקפיצות)
        InteractionManager interactionManager = new InteractionManager(gameManager);

        // 3. יצירת שכבת הקלט/פלט והזרקת שני המנהלים אליה
        ConsoleIO consoleIO = new ConsoleIO(gameManager, interactionManager);

        // 4. התנעה והרצת המשחק
        consoleIO.start();
    }
}