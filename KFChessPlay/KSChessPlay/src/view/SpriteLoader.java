package view;
import graphics.Image;

public class SpriteLoader {
    // בתוך SpriteLoader.java
    public Image getSprite(char type, char color, String state) {
        String folderName = "" + type + color;
        String path = "assets/" + folderName + "/states/" + state + "/sprites/1.png";

        Image sprite = new Image(); // יצירת אובייקט ריק
        sprite.read(path);         // טעינת הנתיב בנפרד
        return sprite;
    }
}