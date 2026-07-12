package Controller;

import Models.Position;

public class CoordinateParser {
    // 🧱 הגדרת גודל המשבצת כקבוע - פותר את בעיית מספר הקסם 100
    public static final int CELL_SIZE = 100;

    public static Position parseClick(int x, int y, int maxRows, int maxCols) {
        int col = x / CELL_SIZE;
        int row = y / CELL_SIZE;

        // בדיקת גבולות מסודרת ישירות בשלב התרגום
        if (row < 0 || row >= maxRows || col < 0 || col >= maxCols) {
            return null;
        }
        return new Position(row, col);
    }
}