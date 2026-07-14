package input;

import models.Position;

/**
 * ה-Mapper הוא הגשר הדו-כיווני:
 * 1. מפיקסלים (עכבר) -> למערכת הקואורדינטות של הלוח.
 * 2. מהלוח (מיקום לוגי) -> לפיקסלים (עבור הרינדור).
 */
public class BoardMapper {

    private static final int PIXELS_PER_TILE = 100;

    /**
     * הופך לחיצת עכבר (פיקסלים) למיקום לוגי (שורה/עמודה).
     * משמש את ה-Controller/InteractionManager.
     */
    public Position pixelToPosition(int pixelX, int pixelY) {
        int row = pixelY / PIXELS_PER_TILE;
        int col = pixelX / PIXELS_PER_TILE;
        return new Position(row, col);
    }

    /**
     * הופך מיקום לוגי (מטרים/שבצות) למיקום פיקסלים על המסך.
     * משמש את ה-ImgRenderer.
     */
    public int toPixelX(double x) {
        return (int) (x * PIXELS_PER_TILE);
    }

    public int toPixelY(double y) {
        return (int) (y * PIXELS_PER_TILE);
    }
}