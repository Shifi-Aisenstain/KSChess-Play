package Engine;

import Controller.MoveCommand;
import Models.Board;
import Models.Piece;
import Models.Position;
import Rules.RuleEngine;
import java.util.ArrayList;
import java.util.List;

public class GameManager {
    private Board board;
    private final RuleEngine ruleEngine = new RuleEngine();
    private final List<GameEvent> activeEvents = new ArrayList<>();
    private long gameClockMs = 0;
    private boolean isGameOver = false;

    public void initializeBoard(int rows, int cols) {
        this.board = new Board(rows, cols);
    }

    public Board getBoard() { return this.board; }
    public boolean isGameOver() { return this.isGameOver; }

    public void addPieceToBoard(int row, int col, Piece piece) {
        if (this.board != null) {
            this.board.setPieceAt(new Position(row, col), piece);
        }
    }

    public void requestMove(MoveCommand command) {
        // אם המשחק נגמר, לא מאפשרים שום מהלך (טסט 38)
        if (isGameOver) return;

        if (ruleEngine.validateMove(command, board, this)) {
            Piece piece = board.getPieceAt(command.getSource());

            // 🔥 תיקון קריטי: חישוב משך הזמן לפי מרחק הצעדים (1000ms לכל משבצת)
            int deltaRow = Math.abs(command.getDestination().getRow() - command.getSource().getRow());
            int deltaCol = Math.abs(command.getDestination().getCol() - command.getSource().getCol());
            int distance = Math.max(deltaRow, deltaCol);
            long duration = distance * 1000L;

            activeEvents.add(new MoveEvent(
                    piece,
                    command.getSource().getRow(), command.getSource().getCol(),
                    command.getDestination().getRow(), command.getDestination().getCol(),
                    gameClockMs + duration
            ));
        }
    }

    public void requestJump(Position pos) {
        if (isGameOver) return;
        Piece piece = board.getPieceAt(pos);
        if (piece != null && !isPieceBusy(pos.getRow(), pos.getCol())) {
            activeEvents.add(new JumpEvent(piece, pos.getRow(), pos.getCol(), gameClockMs + 1000));
        }
    }

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

        // שלב 1: אירועי תנועה
        for (GameEvent event : triggeredEvents) {
            if (event instanceof MoveEvent) {
                event.execute(this.board, snapshot, this);
            }
        }

        // שלב 2: שאר האירועים (כמו קפיצות)
        for (GameEvent event : triggeredEvents) {
            if (!(event instanceof MoveEvent)) {
                event.execute(this.board, snapshot, this);
            }
        }

        activeEvents.removeAll(triggeredEvents);
    }

    public boolean isPieceBusy(int row, int col) {
        return activeEvents.stream().anyMatch(e ->
                (e.getFromRow() == row && e.getFromCol() == col) ||
                        (e instanceof MoveEvent && ((MoveEvent) e).getToRow() == row && ((MoveEvent) e).getToCol() == col)
        );
    }

    public void setGameOver(boolean gameOver) {
        this.isGameOver = gameOver;
    }

    public String[][] getUpdatedBoardMatrix() {
        return board.getReadOnlyMatrixView();
    }
}