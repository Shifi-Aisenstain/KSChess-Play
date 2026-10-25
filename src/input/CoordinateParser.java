package input;

import models.Position;

public class CoordinateParser {
    public static final int CELL_SIZE = 100;

    public static Position parseClick(int x, int y, int maxRows, int maxCols) {
        int col = x / CELL_SIZE;
        int row = y / CELL_SIZE;

        if (row < 0 || row >= maxRows || col < 0 || col >= maxCols) {
            return null;
        }
        return new Position(row, col);
    }
}
