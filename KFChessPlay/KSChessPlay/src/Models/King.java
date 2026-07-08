package Models;

public class King extends Piece {
    public King(char color) { super(color); }

    @Override
    public char getType() { return 'K'; }

    @Override
    public boolean isValidMove(Piece[][] board, int fromRow, int fromCol, int toRow, int toCol) {
        return Math.abs(fromRow - toRow) <= 1 && Math.abs(fromCol - toCol) <= 1;
    }
}