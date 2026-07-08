package Models;

public abstract class Piece {
    protected final char color;

    public Piece(char color) {
        this.color = color;
    }

    public char getColor() {
        return this.color;
    }

    public abstract char getType();

    public abstract boolean isValidMove(Piece[][] board, int fromRow, int fromCol, int toRow, int toCol);

    protected boolean isPathClear(Piece[][] board, int fromRow, int fromCol, int toRow, int toCol) {
        int rowStep = Integer.compare(toRow, fromRow);
        int colStep = Integer.compare(toCol, fromCol);

        int currentRow = fromRow + rowStep;
        int currentCol = fromCol + colStep;

        while (currentRow != toRow || currentCol != toCol) {
            if (board[currentRow][currentCol] != null) {
                return false;
            }
            currentRow += rowStep;
            currentCol += colStep;
        }
        return true;
    }
}