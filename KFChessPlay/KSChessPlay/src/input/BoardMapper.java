package input;

/**
 * Converts logical/engine coordinates (metres, or row/col) into pixel
 * coordinates for rendering. This is a one-way, renderer-facing conversion
 * only. Converting a raw mouse click back into a board Position is a
 * different job with different rules (bounds checking) and belongs to
 * CoordinateParser - keeping the two directions in one class ("the
 * bidirectional bridge") made this class do two unrelated jobs at once.
 */
public class BoardMapper {

    // Public so SpriteLoader can scale sprites to exactly one tile - having
    // two separate "100" constants in two files is how they silently drift
    // apart (which is what caused sprites to be drawn oversized).
    public static final int PIXELS_PER_TILE = 100;

    public int toPixelX(double x) {
        return (int) (x * PIXELS_PER_TILE);
    }

    public int toPixelY(double y) {
        return (int) (y * PIXELS_PER_TILE);
    }
}
