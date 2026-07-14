import engine.GameManager;
import controller.GameController; // ה-Controller שלך שמחליף חלקית את ConsoleIO
import view.*;
import input.BoardMapper;
import graphics.Image;

public class Main {
    public static void main(String[] args) {
        // 1. יצירת רכיבי המנוע
        GameManager gameManager = new GameManager();

        // 2. יצירת רכיבי ה-View והקלט
        SpriteLoader spriteLoader = new SpriteLoader();
        BoardMapper boardMapper = new BoardMapper();

        // טעינת תמונת רקע (דוגמה)
        Image background = new Image();
        background.read("assets/board_layout.png");

        ImgRenderer renderer = new ImgRenderer(spriteLoader, background, boardMapper);

        // 3. ה-Controller מחבר בין ה-Input (Window) ל-Engine
        GameController controller = new GameController(gameManager);

        // 4. הרצת החלון
        GameWindow window = new GameWindow(renderer, boardMapper, controller);
    }
}