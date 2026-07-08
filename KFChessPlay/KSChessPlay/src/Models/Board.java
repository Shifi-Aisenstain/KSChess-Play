package Models;

public class Board {
    private final Piece[][] pieces;
    private final int rows;
    private final int cols;

    public Board(int rows, int cols) {
        this.rows = rows;
        this.cols = cols;
        this.pieces = new Piece[rows][cols];
    }

    public Piece getPieceAt(int row, int col) {
        if (row < 0 || row >= rows || col < 0 || col >= cols) return null;
        return pieces[row][col];
    }

    public void setPieceAt(int row, int col, Piece piece) {
        if (row >= 0 && row < rows && col >= 0 && col < cols) {
            pieces[row][col] = piece;
        }
    }

    public int getLength() { return rows; }

    // 1. מתודה חדשה עבור מספר העמודות (נחוצה ל-GameManager)
    public int getCols() { return cols; }

    // 2. מתודה חדשה להחזרת המטריצה כקריאה בלבד (נחוצה ל-InteractionManager בשורה 42)
    // זה שומר על עקרון ה-Encapsulation כדי שהכלים לא ישנו את הלוח ישירות!
    public Piece[][] getMatrixReadOnly() {
        return this.pieces;
    }
}