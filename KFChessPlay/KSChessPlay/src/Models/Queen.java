package Models;

public class Queen extends Piece {
    public Queen(char color) { super(color); }

    @Override
    public char getType() { return 'Q'; }

    @Override
    public boolean isValidMove(Piece[][] board, int fromRow, int fromCol, int toRow, int toCol) {
        boolean validRookMove = (fromRow == toRow || fromCol == toCol);
        boolean validBishopMove = (Math.abs(fromRow - toRow) == Math.abs(fromCol - toCol));

        if (!validRookMove && !validBishopMove) return false;
        return isPathClear(board, fromRow, fromCol, toRow, toCol);
    }
}