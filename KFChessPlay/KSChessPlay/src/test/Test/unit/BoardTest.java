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
        assertEquals(3, board.getLength()); // שורות
        assertEquals(4, board.getCols());   // עמודות
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
    public void testGetReadOnlyMatrixView() {
        Board board = new Board(2, 2);
        board.setPieceAt(new Position(0, 0), new Piece('b', 'K'));

        String[][] matrix = board.getReadOnlyMatrixView();
        assertEquals("bK", matrix[0][0]);
        assertEquals(".", matrix[0][1]);
    }
}