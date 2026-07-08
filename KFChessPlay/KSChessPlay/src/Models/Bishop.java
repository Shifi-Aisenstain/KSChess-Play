package Models;

public class Bishop extends Piece {
    public Bishop(char color) { super(color); }

    @Override
    public char getType() { return 'B'; }

    @Override
    public boolean isValidMove(Piece[][] board, int fromRow, int fromCol, int toRow, int toCol) {
        if (Math.abs(fromRow - toRow) != Math.abs(fromCol - toCol)) return false;
        return isPathClear(board, fromRow, fromCol, toRow, toCol);
    }
}