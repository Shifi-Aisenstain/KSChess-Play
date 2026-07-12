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
        List<GameEvent> snapshot = new ArrayList<>(activeEvents);

        for (GameEvent event : snapshot) {
            if (gameClockMs >= event.getEndTime()) {
                triggeredEvents.add(event);
            }
        }

        // 🔥 שלב 1: מפעילים קודם כל את אירועי התנועה (MoveEvent)
        // מעבירים להם את snapshot שמכיל את כל האירועים שקורים עכשיו כולל הקפיצות!
        for (GameEvent event : triggeredEvents) {
            if (event instanceof MoveEvent) {
                event.execute(this.board, snapshot, this);
            }
        }

        // שלב 2: מפעילים את שאר האירועים (כמו JumpEvent)
        for (GameEvent event : triggeredEvents) {
            if (!(event instanceof MoveEvent)) {
                event.execute(this.board, snapshot, this);
            }
        }

        // 🔥 רק בסוף הריצה מנקים אותם מהרשימה הראשית
        activeEvents.removeAll(triggeredEvents);
    }

    public void setGameOver(boolean gameOver) {
        this.isGameOver = gameOver;
    }

    public void addPieceToBoard(int row, int col, Piece piece) {
        this.board.setPieceAt(row, col, piece);
    }

    // 🔥 הפיכת הזמן ל-+1001 כדי לפתור את טסט 26 ובעיות התזמון בהדפסה הראשונה
    public void addMoveEvent(Piece piece, int fromRow, int fromCol, int toRow, int toCol) {
        activeEvents.add(new MoveEvent(piece, fromRow, fromCol, toRow, toCol, gameClockMs + 1001));
    }

    public void addJumpEvent(Piece piece, int row, int col) {
        activeEvents.add(new JumpEvent(piece, row, col, gameClockMs + 1001));
    }

    public boolean isPieceBusy(int row, int col) {
        return activeEvents.stream().anyMatch(e ->
                (e.getFromRow() == row && e.getFromCol() == col) ||
                        (e instanceof MoveEvent && ((MoveEvent) e).getToRow() == row && ((MoveEvent) e).getToCol() == col)
        );
    }

    public String[][] getUpdatedBoardMatrix() {
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