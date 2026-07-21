package test.Test.unit;

import realtime.RealTimeArbiter;
import engine.GameManager;
import models.Board;
import models.Piece;
import models.Position;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class RealTimeArbiterTest {

    private RealTimeArbiter arbiter;
    private GameManager gm;

    @BeforeEach
    public void setUp() {
        gm = new GameManager();
        gm.initializeBoard(8, 8);
        arbiter = new RealTimeArbiter();
        arbiter.setBoard(gm.getBoard());
    }

    @Test
    public void testPieceBusyDuringMove() {
        Piece rook = new Piece('w', 'R');
        arbiter.registerMove(rook, new Position(0, 0), new Position(0, 3));
        assertTrue(arbiter.isPieceBusy(0, 0));
        assertFalse(arbiter.isPieceBusy(1, 1));
    }

    @Test
    public void testPieceBusyDuringJump() {
        Piece king = new Piece('w', 'K');
        arbiter.registerJump(king, new Position(3, 3));
        assertTrue(arbiter.isPieceBusy(3, 3));
    }

    @Test
    public void testMoveCompletesAfterTime() {
        Piece rook = new Piece('w', 'R');
        gm.addPieceToBoard(0, 0, rook);
        arbiter.registerMove(rook, new Position(0, 0), new Position(0, 1));
        arbiter.advanceTime(500, gm);
        assertTrue(arbiter.isPieceBusy(0, 0));
        arbiter.advanceTime(500, gm);
        assertFalse(arbiter.isPieceBusy(0, 0));
        assertEquals(rook, gm.getBoard().getPieceAt(new Position(0, 1)));
    }

    @Test
    public void testJumpCompletesAndPieceReturns() {
        Piece king = new Piece('w', 'K');
        gm.addPieceToBoard(3, 3, king);
        gm.getBoard().setPieceAt(new Position(3, 3), null);
        arbiter.registerJump(king, new Position(3, 3));
        arbiter.advanceTime(1000, gm);
        assertEquals(king, gm.getBoard().getPieceAt(new Position(3, 3)));
    }

    @Test
    public void testCooldownAfterMoveKeepsPieceBusy() {
        Piece rook = new Piece('w', 'R');
        gm.addPieceToBoard(0, 0, rook);
        arbiter.registerMove(rook, new Position(0, 0), new Position(0, 1));
        arbiter.advanceTime(1000, gm);
        assertTrue(arbiter.isPieceBusy(0, 1));
        arbiter.advanceTime(3000, gm);
        assertFalse(arbiter.isPieceBusy(0, 1));
    }

    @Test
    public void testShortRestCooldownAfterJump() {
        Piece king = new Piece('w', 'K');
        gm.addPieceToBoard(3, 3, king);
        gm.getBoard().setPieceAt(new Position(3, 3), null);
        arbiter.registerJump(king, new Position(3, 3));
        arbiter.advanceTime(1000, gm);
        assertTrue(arbiter.isPieceBusy(3, 3));
        arbiter.advanceTime(1000, gm);
        assertFalse(arbiter.isPieceBusy(3, 3));
    }

    @Test
    public void testRegisterMoveWithNullBoardDoesNothing() {
        RealTimeArbiter a = new RealTimeArbiter();
        a.registerMove(new Piece('w', 'R'), new Position(0, 0), new Position(0, 3));
        assertFalse(a.isPieceBusy(0, 0));
    }

    @Test
    public void testRegisterJumpWithNullBoardDoesNothing() {
        RealTimeArbiter a = new RealTimeArbiter();
        a.registerJump(new Piece('w', 'K'), new Position(3, 3));
        assertFalse(a.isPieceBusy(3, 3));
    }

    @Test
    public void testGetClockMs() {
        assertEquals(0, arbiter.getClockMs());
        arbiter.advanceTime(500, gm);
        assertEquals(500, arbiter.getClockMs());
    }

    @Test
    public void testReset() {
        Piece rook = new Piece('w', 'R');
        arbiter.registerMove(rook, new Position(0, 0), new Position(0, 3));
        arbiter.advanceTime(500, gm);
        arbiter.reset();
        assertEquals(0, arbiter.getClockMs());
        assertFalse(arbiter.isPieceBusy(0, 0));
        assertTrue(arbiter.getActiveEvents().isEmpty());
    }

    @Test
    public void testGetActiveEventsIsUnmodifiable() {
        assertThrows(UnsupportedOperationException.class, () ->
            arbiter.getActiveEvents().add(null)
        );
    }

    @Test
    public void testMultipleMovesSimultaneous() {
        Piece r1 = new Piece('w', 'R');
        Piece r2 = new Piece('b', 'R');
        gm.addPieceToBoard(0, 0, r1);
        gm.addPieceToBoard(7, 7, r2);
        arbiter.registerMove(r1, new Position(0, 0), new Position(0, 3));
        arbiter.registerMove(r2, new Position(7, 7), new Position(7, 4));
        assertTrue(arbiter.isPieceBusy(0, 0));
        assertTrue(arbiter.isPieceBusy(7, 7));
    }

    @Test
    public void testAdvanceTimeWithNullBoardDoesNotCrash() {
        RealTimeArbiter a = new RealTimeArbiter();
        a.advanceTime(1000, gm);
    }
}
