package test.Test.unit;

import realtime.RealTimeArbiter;
import models.Board;
import models.Piece;
import models.Position;
import engine.GameManager;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class RealTimeArbiterTest {

    @Test
    public void testPieceIsBusyDuringMove() {
        RealTimeArbiter arbiter = new RealTimeArbiter();
        Board board = new Board(5, 5);
        arbiter.setBoard(board);

        Position src = new Position(0, 0);
        Position dest = new Position(0, 2);
        Piece piece = new Piece('w', 'R');

        arbiter.registerMove(piece, src, dest);

        // המשבצות של המקור והיעד צריכות להיות מסומנות כעסוקות בזמן התנועה
        assertTrue(arbiter.isPieceBusy(0, 0));
        assertTrue(arbiter.isPieceBusy(0, 2));
        assertFalse(arbiter.isPieceBusy(1, 1)); // משבצת לא קשורה פנויה
    }

    @Test
    public void testAdvanceTimeCompletesEvent() {
        RealTimeArbiter arbiter = new RealTimeArbiter();
        GameManager gameManager = new GameManager();
        gameManager.initializeBoard(3, 3);
        arbiter.setBoard(gameManager.getBoard());

        Piece piece = new Piece('w', 'R');
        gameManager.addPieceToBoard(0, 0, piece);

        // רישום מהלך של משבצת אחת (לוקח 1000 מילישניות)
        arbiter.registerMove(piece, new Position(0, 0), new Position(0, 1));

        // התקדמות חצי דרך - עדיין עסוק
        arbiter.advanceTime(500, gameManager);
        assertTrue(arbiter.isPieceBusy(0, 0));

        // התקדמות סוף הדרך - המהלך הושלם והאיבר כבר לא עסוק
        arbiter.advanceTime(500, gameManager);
        assertFalse(arbiter.isPieceBusy(0, 0));
        assertEquals(piece, gameManager.getBoard().getPieceAt(new Position(0, 1)));
    }
}