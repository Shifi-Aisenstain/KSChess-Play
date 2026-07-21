package test.Test.unit;

import rules.RuleEngine;
import models.Board;
import models.Piece;
import models.Position;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class RuleEngineTest {

    private RuleEngine engine;
    private Board board;

    @BeforeEach
    public void setUp() {
        engine = new RuleEngine();
        board = new Board(8, 8);
    }

    @Test
    public void testSourceOutOfBounds() {
        assertFalse(engine.validateMove(board, new Position(-1, 0), new Position(0, 0)).isValid());
    }

    @Test
    public void testDestinationOutOfBounds() {
        board.setPieceAt(new Position(0, 0), new Piece('w', 'R'));
        assertFalse(engine.validateMove(board, new Position(0, 0), new Position(8, 0)).isValid());
    }

    @Test
    public void testNoPieceAtSource() {
        assertFalse(engine.validateMove(board, new Position(0, 0), new Position(0, 1)).isValid());
    }

    @Test
    public void testCannotCaptureFriendly() {
        board.setPieceAt(new Position(0, 0), new Piece('w', 'R'));
        board.setPieceAt(new Position(0, 3), new Piece('w', 'N'));
        assertFalse(engine.validateMove(board, new Position(0, 0), new Position(0, 3)).isValid());
    }

    @Test
    public void testRookValidMove() {
        board.setPieceAt(new Position(0, 0), new Piece('w', 'R'));
        assertTrue(engine.validateMove(board, new Position(0, 0), new Position(0, 5)).isValid());
    }

    @Test
    public void testRookBlockedByPiece() {
        board.setPieceAt(new Position(0, 0), new Piece('w', 'R'));
        board.setPieceAt(new Position(0, 2), new Piece('b', 'P'));
        assertFalse(engine.validateMove(board, new Position(0, 0), new Position(0, 5)).isValid());
    }

    @Test
    public void testRookCapturesEnemy() {
        board.setPieceAt(new Position(0, 0), new Piece('w', 'R'));
        board.setPieceAt(new Position(0, 3), new Piece('b', 'P'));
        assertTrue(engine.validateMove(board, new Position(0, 0), new Position(0, 3)).isValid());
    }

    @Test
    public void testBishopValidMove() {
        board.setPieceAt(new Position(4, 4), new Piece('w', 'B'));
        assertTrue(engine.validateMove(board, new Position(4, 4), new Position(6, 6)).isValid());
    }

    @Test
    public void testBishopInvalidMove() {
        board.setPieceAt(new Position(4, 4), new Piece('w', 'B'));
        assertFalse(engine.validateMove(board, new Position(4, 4), new Position(4, 6)).isValid());
    }

    @Test
    public void testQueenValidMoveOrthogonal() {
        board.setPieceAt(new Position(3, 3), new Piece('w', 'Q'));
        assertTrue(engine.validateMove(board, new Position(3, 3), new Position(3, 7)).isValid());
    }

    @Test
    public void testQueenValidMoveDiagonal() {
        board.setPieceAt(new Position(3, 3), new Piece('w', 'Q'));
        assertTrue(engine.validateMove(board, new Position(3, 3), new Position(6, 6)).isValid());
    }

    @Test
    public void testKingValidMove() {
        board.setPieceAt(new Position(4, 4), new Piece('w', 'K'));
        assertTrue(engine.validateMove(board, new Position(4, 4), new Position(4, 5)).isValid());
    }

    @Test
    public void testKingInvalidMove() {
        board.setPieceAt(new Position(4, 4), new Piece('w', 'K'));
        assertFalse(engine.validateMove(board, new Position(4, 4), new Position(4, 6)).isValid());
    }

    @Test
    public void testKnightValidMove() {
        board.setPieceAt(new Position(4, 4), new Piece('w', 'N'));
        assertTrue(engine.validateMove(board, new Position(4, 4), new Position(6, 5)).isValid());
    }

    @Test
    public void testKnightInvalidMove() {
        board.setPieceAt(new Position(4, 4), new Piece('w', 'N'));
        assertFalse(engine.validateMove(board, new Position(4, 4), new Position(4, 5)).isValid());
    }

    @Test
    public void testPawnValidMove() {
        board.setPieceAt(new Position(6, 3), new Piece('w', 'P'));
        assertTrue(engine.validateMove(board, new Position(6, 3), new Position(5, 3)).isValid());
    }

    @Test
    public void testValidateJumpValid() {
        board.setPieceAt(new Position(3, 3), new Piece('w', 'K'));
        assertTrue(engine.validateJump(board, new Position(3, 3)).isValid());
    }

    @Test
    public void testValidateJumpOutOfBounds() {
        assertFalse(engine.validateJump(board, new Position(-1, 0)).isValid());
    }

    @Test
    public void testValidateJumpNoPiece() {
        assertFalse(engine.validateJump(board, new Position(3, 3)).isValid());
    }
}
