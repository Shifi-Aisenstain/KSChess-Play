import view.*;
import engine.GameManager;
import controller.GameController;
import input.BoardMapper;
import graphics.Image;

public class Main {
    public static void main(String[] args) {
        System.out.println("Starting Game...");

        // 1. אתחול מנוע הלוגיקה
        GameManager gameManager = new GameManager();

        // 2. אתחול ה-UI והאדפטרים
        BoardMapper mapper = new BoardMapper();
        GameController controller = new GameController(gameManager);
        SpriteLoader spriteLoader = new SpriteLoader();

        // טעינת תמונת הרקע מהנתיב המדויק שנמצא בתיקיית ה-assets שלך
        Image bg = new Image();
        try {
            bg.read("board.png");// במקום "assets/board.png"
            System.out.println("Board image loaded successfully.");
        } catch (Exception e) {
            System.err.println("Error loading board image: " + e.getMessage());
            return; // עצירה אם התמונה לא נטענה
        }

        ImgRenderer renderer = new ImgRenderer(spriteLoader, bg, mapper);

        // 3. יצירת החלון והרצתו
        System.out.println("Initializing GameWindow...");
        GameWindow window = new GameWindow(renderer, mapper, controller, gameManager);

        // הגדרה שהחלון יציג את עצמו
        window.setVisible(true);
        System.out.println("Window set to visible.");
    }
}