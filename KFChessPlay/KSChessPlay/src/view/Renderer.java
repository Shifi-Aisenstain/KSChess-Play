package view;

import graphics.Image; // הספרייה שקיבלת

public class Renderer {
    private SpriteLoader spriteLoader;
    private static final int PIXELS_PER_METER = 100;

    public Renderer(SpriteLoader spriteLoader) {
        this.spriteLoader = spriteLoader;
    }

    public void render(GameSnapshot snapshot, Image canvas) {
        for (PieceSnapshot piece : snapshot.getPieces()) {
            // כאן ה-Renderer משתמש במשתנים שהגדרנו ב-PieceSnapshot
            Image img = spriteLoader.getSprite(piece.type, piece.color, piece.state);

            // המרה למסך (פיקסלים)
            int x = (int)(piece.x * PIXELS_PER_METER);
            int y = (int)(piece.y  * PIXELS_PER_METER);

            img.drawOn(canvas, x, y);
        }
    }
}