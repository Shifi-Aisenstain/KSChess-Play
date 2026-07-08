package Models;

public class Rook extends Piece {
    public Rook(char color) { super(color); }

    @Override
    public char getType() { return 'R'; }

    @Override
    public boolean isValidMove(Piece[][] board, int fromRow, int fromCol, int toRow, int toCol) {
        if (fromRow != toRow && fromCol != toCol) return false;
        return isPathClear(board, fromRow, fromCol, toRow, toCol);
    }
}