package Models;

public class Knight extends Piece {
    public Knight(char color) { super(color); }

    @Override
    public char getType() { return 'N'; }

    @Override
    public boolean isValidMove(Piece[][] board, int fromRow, int fromCol, int toRow, int toCol) {
        return (Math.abs(fromRow - toRow) == 2 && Math.abs(fromCol - toCol) == 1) ||
                (Math.abs(fromRow - toRow) == 1 && Math.abs(fromCol - toCol) == 2);
    }
}