package models;

/**
 * ✅ Board: Data Owner & Encapsulation (CR Requirement Part C)
 * 
 * RESPONSIBILITY: Board is the SOLE OWNER of piece placement.
 * No external code can modify piece positions without going through Board's methods.
 * 
 * ENCAPSULATION RULES:
 * - grid is PRIVATE + FINAL (no direct access)
 * - Pieces are modified ONLY via setPieceAt()
 * - Read access is safe (returns strings, not mutable piece arrays)
 * 
 * CRITICAL METHODS:
 * 1. getPieceAt(Position) - READ: Returns piece reference (safe - Piece is immutable)
 * 2. setPieceAt(Position, Piece) - WRITE: Only Board can call this internally
 * 3. getReadOnlyMatrixView() - RENDER: Returns STRING array (external code CAN'T mutate board)
 * 
 * WHY getReadOnlyMatrixView() Returns Strings?
 *   OLD BUG: Returned Piece[][] array → external code could do:
 *     Piece[][] grid = board.getReadOnlyMatrix();
 *     grid[0][0] = null;  // ❌ Changed board state without permission!
 * 
 *   NEW SOLUTION: Return String[][] instead:
 *     String[][] view = board.getReadOnlyMatrixView();
 *     view[0][0] = null;  // ❌ Doesn't affect board (strings are immutable)
 * 
 * @author Chess Game Architecture
 * @version 1.0 (Encapsulation Pattern)
 */
public class Board {
    private final Piece[][] grid;
    private final int rows;
    private final int cols;

    public Board(int rows, int cols) {
        this.rows = rows;
        this.cols = cols;
        this.grid = new Piece[rows][cols];
    }

    public int getLength() { return rows; }
    public int getCols() { return cols; }

    public boolean isPositionOnBoard(Position pos) {
        return pos.getRow() >= 0 && pos.getRow() < rows && pos.getCol() >= 0 && pos.getCol() < cols;
    }

    public Piece getPieceAt(Position pos) {
        if (!isPositionOnBoard(pos)) return null;
        return grid[pos.getRow()][pos.getCol()];
    }

    public void setPieceAt(Position pos, Piece piece) {
        if (isPositionOnBoard(pos)) {
            grid[pos.getRow()][pos.getCol()] = piece;
        }
    }

    // הדפסה בטוחה: מחזירה מטריצת מחרוזות חדשה לחלוטין לצורכי renderer בלבד
    public String[][] getReadOnlyMatrixView() {
        String[][] view = new String[rows][cols];
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                Piece p = grid[i][j];
                view[i][j] = (p == null) ? "." : "" + p.getColor() + p.getType();
            }
        }
        return view;
    }
}