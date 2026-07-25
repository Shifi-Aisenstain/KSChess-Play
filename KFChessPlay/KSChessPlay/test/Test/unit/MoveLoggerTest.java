package Test.unit;

import engine.MoveLogger;
import models.Board;
import models.Piece;
import models.Position;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class MoveLoggerTest {

    private final Board board = new Board(8, 8);

    @Test
    public void testNonCaptureMoveIsRecordedForMovingColor() {
        MoveLogger logger = new MoveLogger();
        Piece whitePawn = new Piece('w', 'P');

        logger.recordMove(new Position(6, 4), new Position(4, 4), whitePawn, null, board);

        assertEquals(1, logger.getWhiteMoveHistory().size());
        assertTrue(logger.getBlackMoveHistory().isEmpty());
        assertTrue(logger.getWhiteMoveHistory().get(0).endsWith("Pe4"));
    }

    @Test
    public void testBlackMoveGoesToBlackHistory() {
        MoveLogger logger = new MoveLogger();
        Piece blackPawn = new Piece('b', 'P');

        logger.recordMove(new Position(1, 4), new Position(3, 4), blackPawn, null, board);

        assertEquals(1, logger.getBlackMoveHistory().size());
        assertTrue(logger.getWhiteMoveHistory().isEmpty());
    }

    @Test
    public void testCaptureNotationIncludesX() {
        MoveLogger logger = new MoveLogger();
        Piece whiteKnight = new Piece('w', 'N');
        Piece blackPawn = new Piece('b', 'P');

        logger.recordMove(new Position(2, 2), new Position(4, 4), whiteKnight, blackPawn, board);

        assertTrue(logger.getWhiteMoveHistory().get(0).endsWith("Nxe4"));
    }

    @Test
    public void testCaptureAwardsScoreByPieceValue() {
        MoveLogger logger = new MoveLogger();
        Piece whiteRook = new Piece('w', 'R');
        Piece blackQueen = new Piece('b', 'Q');

        logger.recordMove(new Position(0, 0), new Position(0, 4), whiteRook, blackQueen, board);

        assertEquals(9, logger.getScoreWhite());
        assertEquals(0, logger.getScoreBlack());
    }

    @Test
    public void testCapturingKingAwardsNoScore() {
        MoveLogger logger = new MoveLogger();
        Piece whiteQueen = new Piece('w', 'Q');
        Piece blackKing = new Piece('b', 'K');

        logger.recordMove(new Position(0, 0), new Position(0, 4), whiteQueen, blackKing, board);

        assertEquals(0, logger.getScoreWhite());
    }

    @Test
    public void testKingCaptureNotationOmitsX() {
        MoveLogger logger = new MoveLogger();
        Piece whiteQueen = new Piece('w', 'Q');
        Piece blackKing = new Piece('b', 'K');

        logger.recordMove(new Position(0, 0), new Position(0, 4), whiteQueen, blackKing, board);

        assertTrue(logger.getWhiteMoveHistory().get(0).endsWith("Qe8"));
    }

    @Test
    public void testGetLastEntryReturnsEmptyStringWhenNoMovesYet() {
        MoveLogger logger = new MoveLogger();
        assertEquals("", logger.getLastEntry('w'));
        assertEquals("", logger.getLastEntry('b'));
    }

    @Test
    public void testGetLastEntryReturnsMostRecentMoveForColor() {
        MoveLogger logger = new MoveLogger();
        Piece whitePawn = new Piece('w', 'P');
        logger.recordMove(new Position(6, 0), new Position(5, 0), whitePawn, null, board);
        logger.recordMove(new Position(5, 0), new Position(4, 0), whitePawn, null, board);

        assertTrue(logger.getLastEntry('w').endsWith("Pa4"));
    }

    @Test
    public void testResetClearsScoresAndHistory() {
        MoveLogger logger = new MoveLogger();
        Piece whiteRook = new Piece('w', 'R');
        Piece blackQueen = new Piece('b', 'Q');
        logger.recordMove(new Position(0, 0), new Position(0, 4), whiteRook, blackQueen, board);

        logger.reset();

        assertEquals(0, logger.getScoreWhite());
        assertEquals(0, logger.getScoreBlack());
        assertTrue(logger.getWhiteMoveHistory().isEmpty());
        assertTrue(logger.getBlackMoveHistory().isEmpty());
    }

    @Test
    public void testBlackCaptureAwardsScoreToBlack() {
        MoveLogger logger = new MoveLogger();
        Piece blackKnight = new Piece('b', 'N');
        Piece whiteRook = new Piece('w', 'R');

        logger.recordMove(new Position(1, 1), new Position(3, 2), blackKnight, whiteRook, board);

        assertEquals(5, logger.getScoreBlack());
        assertEquals(0, logger.getScoreWhite());
        assertEquals(1, logger.getBlackMoveHistory().size());
    }

    @Test
    public void testEachCapturedPieceTypeHasAPositiveValue() {
        for (char capturedType : new char[]{'N', 'B', 'R', 'Q', 'P'}) {
            MoveLogger logger = new MoveLogger();
            Piece whiteRook = new Piece('w', 'R');
            Piece captured = new Piece('b', capturedType);
            logger.recordMove(new Position(0, 0), new Position(0, 1), whiteRook, captured, board);
            assertTrue(logger.getScoreWhite() > 0, "expected a positive value for captured type " + capturedType);
        }
    }

    @Test
    public void testSquareNameFallsBackToRowPlusOneWithoutABoard() {
        MoveLogger logger = new MoveLogger();
        Piece whitePawn = new Piece('w', 'P');
        logger.recordMove(new Position(6, 4), new Position(4, 4), whitePawn, null, null);
        // Without a board to read height from, rank falls back to row+1 (row 4 -> rank 5) instead
        // of the usual board.getLength()-row inversion.
        assertTrue(logger.getWhiteMoveHistory().get(0).endsWith("Pe5"));
    }

    @Test
    public void testMoveHistoryEntryHasElapsedTimePrefix() {
        MoveLogger logger = new MoveLogger();
        Piece whitePawn = new Piece('w', 'P');
        logger.recordMove(new Position(6, 4), new Position(4, 4), whitePawn, null, board);

        assertTrue(logger.getWhiteMoveHistory().get(0).matches("\\d{2}:\\d{2}\\.\\d{3}\\|.*"));
    }
}
