package view;
import graphics.Image;
import input.BoardMapper; // ודאי שזה הייבוא קיים

public class ImgRenderer {
    private SpriteLoader spriteLoader;
    private Image backgroundImg;
    private BoardMapper mapper; // 1. הוספת המשתנה כ-field

    // 2. עדכון הבנאי שיקבל את ה-mapper
    public ImgRenderer(SpriteLoader spriteLoader, Image backgroundImg, BoardMapper mapper) {
        this.spriteLoader = spriteLoader;
        this.backgroundImg = backgroundImg;
        this.mapper = mapper; // 3. שמירת ה-mapper
    }

    public void render(GameSnapshot snapshot, Image canvas) {
        backgroundImg.drawOn(canvas, 0, 0);

        for (PieceSnapshot piece : snapshot.getPieces()) {
            Image sprite = spriteLoader.getSprite(piece.type, piece.color, piece.state);

            // 4. עכשיו mapper מוכר, והשגיאה תעלם!
            int x = mapper.toPixelX(piece.x);
            int y = mapper.toPixelY(piece.y);

            sprite.drawOn(canvas, x, y);
        }
    }
}