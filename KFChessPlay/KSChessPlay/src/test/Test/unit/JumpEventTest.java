package test.Test.unit;

import engine.JumpEvent;
import engine.GameManager;
import models.Board;
import models.Piece;
import models.Position;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import static org.junit.jupiter.api.Assertions.*;

public class JumpEventTest {

    @Test
    public void testJumpEventGettersAndExecution() {
        Piece piece = new Piece('w', 'K');
        Position pos = new Position(3, 3);
        long endTime = System.currentTimeMillis() + 1000;

        // 1. יצירת האובייקט ובדיקת ה-Getters וה-Priority
        JumpEvent event = new JumpEvent(piece, pos, endTime);

        assertEquals(piece, event.getPiece());
        assertEquals(pos, event.getFromPosition());
        assertEquals(endTime, event.getEndTime());
        assertEquals(2, event.getPriority());

        // 2. בדיקת מתודת ה-execute שמחזירה את הכלי ללוח
        Board board = new Board(8, 8);
        GameManager gameManager = new GameManager();
        gameManager.initializeBoard(8, 8);

        // ודאי שהמשבצת ריקה לפני נחיתת הקפיצה
        assertNull(board.getPieceAt(pos));

        // הרצת האירוע
        event.execute(board, new ArrayList<>(), gameManager);

        // וידוא שהכלי חזר ונחת בהצלחה על הלוח במשבצת המקורית
        assertEquals(piece, board.getPieceAt(pos));
    }
}