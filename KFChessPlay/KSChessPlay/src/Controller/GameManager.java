package Controller;

import Models.Board;
import Models.Piece;
import java.util.ArrayList;
import java.util.List;

public class GameManager {
    private Board board;
    private final List<GameEvent> activeEvents = new ArrayList<>();
    private long gameClockMs = 0;
    private boolean isGameOver = false;

    public void initializeBoard(int rows, int cols) {
        this.board = new Board(rows, cols);
    }

    public Board getBoard() { return this.board; }
    public boolean isGameOver() { return this.isGameOver; }

    public void handleWait(int ms) {
        this.gameClockMs += ms;
        updateGame();
    }

    public void updateGame() {
        List<GameEvent> triggeredEvents = new ArrayList<>();
        for (GameEvent event : activeEvents) {
            if (gameClockMs >= event.getEndTime()) triggeredEvents.add(event);
        }

        for (GameEvent event : triggeredEvents) {
            if (event.getType() == GameEvent.EventType.MOVE) handleMoveArrival(event);
            else activeEvents.remove(event);
        }
    }

    private void handleMoveArrival(GameEvent moveEvent) {
        int toRow = moveEvent.getToRow();
        int toCol = moveEvent.getToCol();

        boolean captured = activeEvents.stream().anyMatch(e ->
                e.getType() == GameEvent.EventType.JUMP && e.getFromRow() == toRow && e.getFromCol() == toCol);

        if (captured) {
            board.setPieceAt(moveEvent.getFromRow(), moveEvent.getFromCol(), null);
        } else {
            Piece target = board.getPieceAt(toRow, toCol);
            if (target != null && target.getType() == 'K') this.isGameOver = true;

            board.setPieceAt(toRow, toCol, moveEvent.getPiece());
            board.setPieceAt(moveEvent.getFromRow(), moveEvent.getFromCol(), null);
        }
        activeEvents.remove(moveEvent);
    }

    public void addEvent(GameEvent.EventType type, Piece piece, int fromRow, int fromCol, int toRow, int toCol) {
        activeEvents.add(new GameEvent(type, piece, fromRow, fromCol, toRow, toCol, gameClockMs + 1000));
    }

    public boolean isPieceBusy(int row, int col) {
        return activeEvents.stream().anyMatch(e -> e.getFromRow() == row && e.getFromCol() == col);
    }

    /**
     * 🎯 המתודה שהייתה חסרה ומייצרת את המטריצה עבור ה-IO
     */
    public String[][] getUpdatedBoardMatrix() {
        updateGame();
        int rows = board.getLength();
        int cols = board.getCols();
        String[][] matrix = new String[rows][cols];

        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                Piece p = board.getPieceAt(i, j);
                matrix[i][j] = (p == null) ? "." : "" + p.getColor() + p.getType();
            }
        }
        return matrix;
    }
}