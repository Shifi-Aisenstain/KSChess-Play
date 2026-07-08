package Models;

public class Pawn extends Piece {
    public Pawn(char color) { super(color); }

    @Override
    public char getType() { return 'P'; }

    @Override
    public boolean isValidMove(Piece[][] board, int fromRow, int fromCol, int toRow, int toCol) {
        int numRows = board.length;
        int rowDiff = toRow - fromRow;
        int colDiff = Math.abs(toCol - fromCol);

        if (this.color == 'w') {
            if (rowDiff == -1 && colDiff == 0 && board[toRow][toCol] == null) {
                return true;
            }
            if (fromRow == numRows - 1 && rowDiff == -2 && colDiff == 0 && board[toRow][toCol] == null) {
                return isPathClear(board, fromRow, fromCol, toRow, toCol);
            }
            // אכילה באלכסון
            if (rowDiff == -1 && colDiff == 1 && board[toRow][toCol] != null && board[toRow][toCol].getColor() == 'b') {
                return true;
            }
        }

        if (this.color == 'b') {
            // צעד אחד קדימה
            if (rowDiff == 1 && colDiff == 0 && board[toRow][toCol] == null) {
                return true;
            }
            // צעד כפול משורת ההתחלה
            if (fromRow == 0 && rowDiff == 2 && colDiff == 0 && board[toRow][toCol] == null) {
                return isPathClear(board, fromRow, fromCol, toRow, toCol);
            }
            // אכילה באלכסון
            if (rowDiff == 1 && colDiff == 1 && board[toRow][toCol] != null && board[toRow][toCol].getColor() == 'w') {
                return true;
            }
        }
        return false;
    }
}