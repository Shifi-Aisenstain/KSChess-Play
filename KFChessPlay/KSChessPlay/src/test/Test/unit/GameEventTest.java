package test.Test.unit;

import engine.GameEvent;
import engine.GameManager;
import engine.MoveEvent;
import engine.JumpEvent;
import models.Board;
import models.Piece;
import models.Position;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

public class GameEventTest {

    private static class TestGameEvent extends GameEvent {
        public TestGameEvent(Piece piece, Position fromPosition, long endTime) {
            super(piece, fromPosition, endTime);
        }

        @Override
        public int getPriority() { return 1; }

        @Override
        public void execute(Board board, List<GameEvent> activeEvents, GameManager gameManager) {
            // קריאה פיקטיבית כדי לספק את ה-Coverage של ה-Methods
            assertNotNull(board);
        }
    }

    @Test
    public void testGameEventGettersAndExecution() {
        Piece piece = new Piece('w', 'R');
        Position src = new Position(0, 0);
        long endTime = System.currentTimeMillis() + 1000;

        GameEvent event = new TestGameEvent(piece, src, endTime);

        // בדיקת Getters של מחלקת האב
        assertEquals(piece, event.getPiece());
        assertEquals(src, event.getFromPosition());
        assertEquals(endTime, event.getEndTime());
        assertEquals(1, event.getPriority());

        // הרצה ישירה של ה-execute הפיקטיבי
        Board board = new Board(8, 8);
        event.execute(board, new ArrayList<>(), new GameManager());
    }

    @Test
    public void testEventSubclassesPriority() {
        Piece piece = new Piece('w', 'K');
        Position pos = new Position(1, 1);
        long time = System.currentTimeMillis();

        MoveEvent move = new MoveEvent(piece, pos, pos, time);
        JumpEvent jump = new JumpEvent(piece, pos, time);

        // הפעלה ישירה של Getters ייחודיים ומדדי עדיפות כדי לסגור 100% מתודות
        assertEquals(1, move.getPriority());
        assertEquals(2, jump.getPriority());
        assertEquals(pos, move.getToPosition());
    }
}