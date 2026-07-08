package Controller;

import Models.Piece;

public class InteractionManager {
    private final GameManager gameManager;
    private int selectedRow = -1;
    private int selectedCol = -1;

    public InteractionManager(GameManager gameManager) {
        this.gameManager = gameManager;
    }

    public void handleClick(int x, int y) {
        if (gameManager.isGameOver()) return;
        gameManager.updateGame();

        int col = x / 100;
        int row = y / 100;

        if (gameManager.isPieceBusy(row, col)) return;

        Piece clicked = gameManager.getBoard().getPieceAt(row, col);

        if (clicked != null) {
            if (selectedRow == -1 || gameManager.getBoard().getPieceAt(selectedRow, selectedCol).getColor() == clicked.getColor()) {
                selectedRow = row;
                selectedCol = col;
            } else {
                tryMove(selectedRow, selectedCol, row, col);
            }
        } else if (selectedRow != -1) {
            tryMove(selectedRow, selectedCol, row, col);
        }
    }

    private void tryMove(int fromRow, int fromCol, int toRow, int toCol) {
        Piece piece = gameManager.getBoard().getPieceAt(fromRow, fromCol);
        if (piece.isValidMove(gameManager.getBoard().getMatrixReadOnly(), fromRow, fromCol, toRow, toCol)) {
            gameManager.addEvent(GameEvent.EventType.MOVE, piece, fromRow, fromCol, toRow, toCol);
            selectedRow = -1;
            selectedCol = -1;
        }
    }

    public void handleJump(int row, int col) {
        gameManager.updateGame();
        Piece piece = gameManager.getBoard().getPieceAt(row, col);
        if (piece != null && !gameManager.isPieceBusy(row, col)) {
            gameManager.addEvent(GameEvent.EventType.JUMP, piece, row, col, row, col);
        }
    }
}