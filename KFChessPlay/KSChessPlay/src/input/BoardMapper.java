package input;

public class BoardMapper {

    public static final int PIXELS_PER_TILE = 100;

    public int toPixelX(double x) {
        return (int) (x * PIXELS_PER_TILE);
    }

    public int toPixelY(double y) {
        return (int) (y * PIXELS_PER_TILE);
    }
}
