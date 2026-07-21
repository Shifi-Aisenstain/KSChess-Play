package test.Test.unit;

import rules.PieceRules;
import rules.MoveValidation;
import models.Piece;
import models.Board;
import models.Position;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

public class PieceRulesTest {

    private final PieceRules pieceRules = new PieceRules();

    @Test
    public void testRookHas4Directions() {
        List<int[]> dirs = pieceRules.getRayDirections(new Piece('w', 'R'));
        assertEquals(4, dirs.size());
    }

    @Test
    public void testBishopHas4Directions() {
        List<int[]> dirs = pieceRules.getRayDirections(new Piece('w', 'B'));
        assertEquals(4, dirs.size());
        assertTrue(dirs.stream().anyMatch(d -> d[0] == 1 && d[1] == 1));
    }

    @Test
    public void testQueenHas8Directions() {
        List<int[]> dirs = pieceRules.getRayDirections(new Piece('w', 'Q'));
        assertEquals(8, dirs.size());
    }

    @Test
    public void testNonRayPieceReturnsEmpty() {
        assertTrue(pieceRules.getRayDirections(new Piece('w', 'K')).isEmpty());
        assertTrue(pieceRules.getRayDirections(new Piece('w', 'N')).isEmpty());
        assertTrue(pieceRules.getRayDirections(new Piece('w', 'P')).isEmpty());
    }

    @Test
    public void testNullPieceRayDirections() {
        assertTrue(pieceRules.getRayDirections(null).isEmpty());
    }

    @Test
    public void testKingHas8Offsets() {
        List<int[]> offsets = pieceRules.getFixedOffsets(new Piece('w', 'K'));
        assertEquals(8, offsets.size());
    }

    @Test
    public void testKnightHas8Offsets() {
        List<int[]> offsets = pieceRules.getFixedOffsets(new Piece('w', 'N'));
        assertEquals(8, offsets.size());
        assertTrue(offsets.stream().anyMatch(o -> o[0] == 2 && o[1] == 1));
    }

    @Test
    public void testNonFixedPieceReturnsEmpty() {
        assertTrue(pieceRules.getFixedOffsets(new Piece('w', 'R')).isEmpty());
        assertTrue(pieceRules.getFixedOffsets(new Piece('w', 'P')).isEmpty());
    }

    @Test
    public void testNullPieceFixedOffsets() {
        assertTrue(pieceRules.getFixedOffsets(null).isEmpty());
    }

    @Test
    public void testPawnSingleStepForwardWhite() {
        Board board = new Board(8, 8);
        Piece pawn = new Piece('w', 'P');
        assertTrue(pieceRules.validatePawnMove(pawn, new Position(4, 3), new Position(3, 3), board).isValid());
    }

    @Test
    public void testPawnSingleStepForwardBlack() {
        Board board = new Board(8, 8);
        Piece pawn = new Piece('b', 'P');
        assertTrue(pieceRules.validatePawnMove(pawn, new Position(3, 3), new Position(4, 3), board).isValid());
    }

    @Test
    public void testPawnCannotMoveBackward() {
        Board board = new Board(8, 8);
        assertFalse(pieceRules.validatePawnMove(new Piece('w', 'P'), new Position(4, 3), new Position(5, 3), board).isValid());
        assertFalse(pieceRules.validatePawnMove(new Piece('b', 'P'), new Position(4, 3), new Position(3, 3), board).isValid());
    }

    @Test
    public void testPawnDoubleStepFromStart() {
        Board board = new Board(8, 8);
        Piece pawn = new Piece('w', 'P');
        assertTrue(pieceRules.validatePawnMove(pawn, new Position(6, 3), new Position(4, 3), board).isValid());
    }

    @Test
    public void testPawnDoubleStepBlockedByIntermediate() {
        Board board = new Board(8, 8);
        board.setPieceAt(new Position(5, 3), new Piece('b', 'P'));
        Piece pawn = new Piece('w', 'P');
        assertFalse(pieceRules.validatePawnMove(pawn, new Position(6, 3), new Position(4, 3), board).isValid());
    }

    @Test
    public void testPawnDoubleStepBlockedByDest() {
        Board board = new Board(8, 8);
        board.setPieceAt(new Position(4, 3), new Piece('b', 'P'));
        Piece pawn = new Piece('w', 'P');
        assertFalse(pieceRules.validatePawnMove(pawn, new Position(6, 3), new Position(4, 3), board).isValid());
    }

    @Test
    public void testPawnDiagonalCapture() {
        Board board = new Board(8, 8);
        board.setPieceAt(new Position(3, 4), new Piece('b', 'R'));
        Piece pawn = new Piece('w', 'P');
        assertTrue(pieceRules.validatePawnMove(pawn, new Position(4, 3), new Position(3, 4), board).isValid());
    }

    @Test
    public void testPawnDiagonalCaptureEmptySquare() {
        Board board = new Board(8, 8);
        Piece pawn = new Piece('w', 'P');
        assertFalse(pieceRules.validatePawnMove(pawn, new Position(4, 3), new Position(3, 4), board).isValid());
    }

    @Test
    public void testPawnDiagonalCaptureFriendly() {
        Board board = new Board(8, 8);
        board.setPieceAt(new Position(3, 4), new Piece('w', 'R'));
        Piece pawn = new Piece('w', 'P');
        assertFalse(pieceRules.validatePawnMove(pawn, new Position(4, 3), new Position(3, 4), board).isValid());
    }

    @Test
    public void testPawnForwardBlockedByPiece() {
        Board board = new Board(8, 8);
        board.setPieceAt(new Position(3, 3), new Piece('b', 'R'));
        Piece pawn = new Piece('w', 'P');
        assertFalse(pieceRules.validatePawnMove(pawn, new Position(4, 3), new Position(3, 3), board).isValid());
    }

    @Test
    public void testPawnInvalidMoveCompletely() {
        Board board = new Board(8, 8);
        Piece pawn = new Piece('w', 'P');
        assertFalse(pieceRules.validatePawnMove(pawn, new Position(4, 3), new Position(2, 3), board).isValid());
    }

    @Test
    public void testNonPawnPassedToPawnValidator() {
        Board board = new Board(8, 8);
        Piece rook = new Piece('w', 'R');
        assertFalse(pieceRules.validatePawnMove(rook, new Position(0, 0), new Position(1, 0), board).isValid());
    }
}
