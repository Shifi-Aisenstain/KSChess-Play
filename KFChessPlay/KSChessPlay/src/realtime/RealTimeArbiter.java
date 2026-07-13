package realtime;

import models.Board;
import models.Piece;
import models.Position;
import engine.GameEvent;
import engine.MoveEvent;
import engine.JumpEvent;
import engine.GameManager;
import java.util.ArrayList;
import java.util.List;

public class RealTimeArbiter {
    public static final long TIME_PER_CELL_MS = 1000L;
    public static final long JUMP_DURATION_MS = 1000L;

    private Board board;
    private final List<GameEvent> activeEvents = new ArrayList<>();
    private long gameClockMs = 0;

    public void setBoard(Board board) {
        this.board = board;
    }

    public void registerMove(Piece piece, Position src, Position dest) {
        if (board == null) return;

        int deltaRow = Math.abs(dest.getRow() - src.getRow());
        int deltaCol = Math.abs(dest.getCol() - src.getCol());
        int distance = Math.max(deltaRow, deltaCol);

        long duration = distance * TIME_PER_CELL_MS;

        activeEvents.add(new MoveEvent(piece, src, dest, gameClockMs + duration));
        // 🔥 תיקון טסט 25+26: מחקנו את board.setPieceAt(src, null);
        // הכלי נשאר במקור שלו עד שהוא מגיע ליעד!
    }

    public void registerJump(Piece piece, Position pos) {
        if (board == null) return;

        activeEvents.add(new JumpEvent(piece, pos, gameClockMs + JUMP_DURATION_MS));
        // 🔥 תיקון טסט 51: מחקנו את board.setPieceAt(pos, null);
        // הכלי נשאר על הלוח ומגן על עצמו מפני כלים ידידותיים
    }

    public void advanceTime(int ms, GameManager gameManager) {
        this.gameClockMs += ms;
        updateEvents(gameManager);
    }

    private void updateEvents(GameManager gameManager) {
        if (board == null) return;

        List<GameEvent> triggeredEvents = new ArrayList<>();
        List<GameEvent> snapshot = new ArrayList<>(activeEvents);

        for (GameEvent event : snapshot) {
            if (gameClockMs >= event.getEndTime()) {
                triggeredEvents.add(event);
            }
        }

        triggeredEvents.sort((e1, e2) -> Integer.compare(e1.getPriority(), e2.getPriority()));

        for (GameEvent event : triggeredEvents) {
            event.execute(this.board, snapshot, gameManager);
        }

        activeEvents.removeAll(triggeredEvents);
    }

    public boolean isPieceBusy(int row, int col) {
        Position checkPos = new Position(row, col);
        return activeEvents.stream().anyMatch(e ->
                e.getFromPosition().equals(checkPos) ||
                        (e instanceof MoveEvent && ((MoveEvent) e).getToPosition().equals(checkPos))
        );
    }
}