package input;

import models.Position;

/**
 * ✅ CoordinateParser: Pixel-to-Board Translation Layer (CR Requirement Part A)
 * 
 * PROBLEM SOLVED: The magic number 100 is a UI detail, not chess logic.
 * 
 * OLD PROBLEM:
 *   - Pixel parsing logic mixed in controller
 *   - Hard-coded "100" constant scattered everywhere
 *   - Impossible to change cell size without searching entire codebase
 * 
 * NEW SOLUTION:
 *   - CoordinateParser centralizes ALL pixel-to-grid translation
 *   - CELL_SIZE constant defined here (single source of truth)
 *   - Bounds checking included
 * 
 * USED BY:
 *   - InteractionManager.handleClick() - translates user clicks
 *   - InteractionManager.handleJump() - translates jump requests
 * 
 * EXAMPLE USAGE:
 *   Position pos = CoordinateParser.parseClick(50, 150, 3, 3);
 *   // Returns: Position[0, 1] (assuming CELL_SIZE=100)
 *   
 *   Position oob = CoordinateParser.parseClick(350, 350, 3, 3);
 *   // Returns: null (out of bounds)
 * 
 * @author Chess Game Architecture
 * @version 1.0 (Pixel Parser Pattern)
 */
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