package test.Test.unit;

import models.Board;
import models.Piece;
import models.Position;
import io.BoardPrinter;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class BoardPrinterTest {

    @Test
    public void testGetReadOnlyMatrixViewFormat() {
        Board board = new Board(2, 2);
        board.setPieceAt(new Position(0, 0), new Piece('w', 'R'));

        String[][] matrix = board.getReadOnlyMatrixView();
        assertNotNull(matrix);
        assertEquals("wR", matrix[0][0]);
        assertEquals(".", matrix[0][1]);
    }
}