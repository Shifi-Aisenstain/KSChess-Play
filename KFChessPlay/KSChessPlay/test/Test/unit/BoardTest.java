package test.Test.unit;

import models.Board;
import models.Piece;
import models.Position;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class BoardTest {

    @Test
    public void testBoardInitialization() {
        Board board = new Board(3, 4);
        assertEquals(3, board.getLength());
        assertEquals(4, board.getCols());
    }

    @Test
    public void testSetAndGetPiece() {
        Board board = new Board(3, 3);
        Position pos = new Position(1, 1);
        Piece piece = new Piece('w', 'R');
        board.setPieceAt(pos, piece);
        assertEquals(piece, board.getPieceAt(pos));
    }

    @Test
    public void testGetPieceReturnsNullForEmpty() {
        Board board = new Board(3, 3);
        assertNull(board.getPieceAt(new Position(0, 0)));
    }

    @Test
    public void testGetPieceOutOfBoundsReturnsNull() {
        Board board = new Board(3, 3);
        assertNull(board.getPieceAt(new Position(-1, 0)));
        assertNull(board.getPieceAt(new Position(0, -1)));
        assertNull(board.getPieceAt(new Position(3, 0)));
        assertNull(board.getPieceAt(new Position(0, 3)));
    }

    @Test
    public void testSetPieceOutOfBoundsDoesNothing() {
        Board board = new Board(3, 3);
        board.setPieceAt(new Position(5, 5), new Piece('w', 'R'));
        assertNull(board.getPieceAt(new Position(2, 2)));
    }

    @Test
    public void testSetPieceToNull() {
        Board board = new Board(3, 3);
        Position pos = new Position(1, 1);
        board.setPieceAt(pos, new Piece('w', 'R'));
        board.setPieceAt(pos, null);
        assertNull(board.getPieceAt(pos));
    }

    @Test
    public void testIsPositionOnBoard() {
        Board board = new Board(8, 8);
        assertTrue(board.isPositionOnBoard(new Position(0, 0)));
        assertTrue(board.isPositionOnBoard(new Position(7, 7)));
        assertFalse(board.isPositionOnBoard(new Position(-1, 0)));
        assertFalse(board.isPositionOnBoard(new Position(8, 0)));
        assertFalse(board.isPositionOnBoard(new Position(0, 8)));
    }

    @Test
    public void testGetReadOnlyMatrixView() {
        Board board = new Board(2, 2);
        board.setPieceAt(new Position(0, 0), new Piece('b', 'K'));
        String[][] matrix = board.getReadOnlyMatrixView();
        assertEquals("bK", matrix[0][0]);
        assertEquals(".", matrix[0][1]);
        assertEquals(".", matrix[1][0]);
        assertEquals(".", matrix[1][1]);
    }

    @Test
    public void testMatrixViewIsIndependent() {
        Board board = new Board(2, 2);
        board.setPieceAt(new Position(0, 0), new Piece('w', 'R'));
        String[][] matrix = board.getReadOnlyMatrixView();
        matrix[0][0] = "HACKED";
        assertEquals("wR", board.getReadOnlyMatrixView()[0][0]);
    }
}
