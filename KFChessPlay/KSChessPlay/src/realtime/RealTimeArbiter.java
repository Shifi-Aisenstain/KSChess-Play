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

/**
 * ✅ RealTimeArbiter: Chess Game Real-Time Orchestrator (CR Requirement Part D)
 * 
 * RESPONSIBILITY: Manage real-time movement, timing, and collision detection.
 * This is the HEART of animating moves and handling simultaneous piece movements.
 * 
 * KEY COMPONENTS:
 * 1. activeEvents List - Tracks all in-flight moves and jumps
 * 2. gameClockMs - Central clock for the entire game
 * 3. Priority system - Determines execution order (moves before jumps)
 * 
 * MOVE LIFECYCLE:
 *   Phase 1: registerMove(piece, src, dest)
 *     → Create MoveEvent with duration based on distance
 *     → Add to activeEvents (piece still visible at source)
 *     → isPieceBusy() returns true for src (can't move again until arrival)
 *   
 *   Phase 2: advanceTime(ms)
 *     → gameClockMs += ms
 *     → Check which events reached endTime
 *     → Execute them in priority order
 *   
 *   Phase 3: event.execute()
 *     → Move piece to destination
 *     → Handle captures
 *     → Remove from activeEvents
 * 
 * JUMP MECHANICS (Air Capture):
 *   - registerJump() creates JumpEvent
 *   - Jumping piece stays visible (protects its square)
 *   - When move arrives during jump: MoveEvent.execute() detects collision
 *   - Arriving piece is captured (never lands)
 * 
 * PRIORITY SYSTEM:
 *   - MoveEvent: Priority 1 (executes first)
 *   - JumpEvent: Priority 2 (executes second)
 *   - Enables air capture: arriving move is checked DURING execution
 * 
 * @author Chess Game Architecture
 * @version 1.0 (Real-Time Orchestration)
 */
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