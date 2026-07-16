package realtime;

import models.Board;
import models.Piece;
import models.Position;
import engine.GameEvent;
import engine.MoveEvent;
import engine.JumpEvent;
import engine.CooldownEvent;
import engine.GameManager;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class RealTimeArbiter {
    public static final long TIME_PER_CELL_MS = 1000L;
    public static final long JUMP_DURATION_MS  = 1000L;
    public static final long LONG_REST_MS      = 3000L;
    public static final long SHORT_REST_MS     = 1000L;

    private Board board;
    private final List<GameEvent> activeEvents = new ArrayList<>();
    private long gameClockMs = 0;

    public void setBoard(Board board) {
        this.board = board;
    }

    public void registerMove(Piece piece, Position src, Position dest) {
        if (board == null) return;

        int distance = Math.max(
            Math.abs(dest.getRow() - src.getRow()),
            Math.abs(dest.getCol() - src.getCol())
        );
        long moveDuration = distance * TIME_PER_CELL_MS;
        long moveEnd = gameClockMs + moveDuration;

        activeEvents.add(new MoveEvent(piece, src, dest, moveEnd));
    }

    public void registerJump(Piece piece, Position pos) {
        if (board == null) return;

        long jumpEnd = gameClockMs + JUMP_DURATION_MS;
        activeEvents.add(new JumpEvent(piece, pos, jumpEnd));
        activeEvents.add(new CooldownEvent(piece, pos, gameClockMs + JUMP_DURATION_MS + SHORT_REST_MS,
                CooldownEvent.Type.SHORT_REST));
    }

    public void registerLongRestCooldown(Piece piece, Position landingPos) {
        activeEvents.add(new CooldownEvent(piece, landingPos,
                gameClockMs + LONG_REST_MS, CooldownEvent.Type.LONG_REST));
    }

    public void advanceTime(int ms, GameManager gameManager) {
        this.gameClockMs += ms;
        updateEvents(gameManager);
    }

    private void updateEvents(GameManager gameManager) {
        if (board == null) return;

        List<GameEvent> triggered = new ArrayList<>();
        List<GameEvent> snapshot = new ArrayList<>(activeEvents);

        for (GameEvent event : snapshot) {
            if (gameClockMs >= event.getEndTime()) {
                triggered.add(event);
            }
        }

        triggered.sort((e1, e2) -> Integer.compare(e1.getPriority(), e2.getPriority()));

        for (GameEvent event : triggered) {
            event.execute(this.board, snapshot, gameManager);
        }

        activeEvents.removeAll(triggered);
    }

    public boolean isPieceBusy(int row, int col) {
        Position pos = new Position(row, col);
        for (GameEvent e : activeEvents) {
            if (e instanceof MoveEvent && e.getFromPosition().equals(pos)) return true;
            if (e instanceof JumpEvent && e.getFromPosition().equals(pos)) return true;
            if (e instanceof CooldownEvent && e.getFromPosition().equals(pos)) return true;
        }
        return false;
    }

    public long getClockMs() { return gameClockMs; }

    public List<GameEvent> getActiveEvents() {
        return Collections.unmodifiableList(activeEvents);
    }

    public void reset() {
        activeEvents.clear();
        gameClockMs = 0;
    }
}
