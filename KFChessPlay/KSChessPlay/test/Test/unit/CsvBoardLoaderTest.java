package Test.unit;

import io.CsvBoardLoader;
import models.Board;
import models.Piece;
import models.Position;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class CsvBoardLoaderTest {

    private final CsvBoardLoader loader = new CsvBoardLoader();

    @Test
    public void testLoadsPiecesAtCorrectPositions(@TempDir Path tempDir) throws IOException {
        Path csv = tempDir.resolve("board.csv");
        Files.writeString(csv, "RB,.,KW\n.,.,.\n");

        Board board = loader.load(csv.toString());

        assertEquals(2, board.getLength());
        assertEquals(3, board.getCols());

        Piece rook = board.getPieceAt(new Position(0, 0));
        assertNotNull(rook);
        assertEquals('R', rook.getType());
        assertEquals('b', rook.getColor());

        Piece king = board.getPieceAt(new Position(0, 2));
        assertNotNull(king);
        assertEquals('K', king.getType());
        assertEquals('w', king.getColor());
    }

    @Test
    public void testDotIsAnEmptySquare(@TempDir Path tempDir) throws IOException {
        Path csv = tempDir.resolve("board.csv");
        Files.writeString(csv, ".,.\n.,.\n");

        Board board = loader.load(csv.toString());

        assertNull(board.getPieceAt(new Position(0, 0)));
        assertNull(board.getPieceAt(new Position(1, 1)));
    }

    @Test
    public void testBlankLinesAreSkipped(@TempDir Path tempDir) throws IOException {
        Path csv = tempDir.resolve("board.csv");
        Files.writeString(csv, "RB,.\n\n.,KW\n");

        Board board = loader.load(csv.toString());

        assertEquals(2, board.getLength());
    }

    @Test
    public void testMissingFileThrowsIOException() {
        assertThrows(IOException.class, () -> loader.load("no/such/file.csv"));
    }

    @Test
    public void testRealAssetBoardCsvLoadsAsStandardEightByEightOpening() throws IOException {
        Board board = loader.load("assets/board.csv");

        assertEquals(8, board.getLength());
        assertEquals(8, board.getCols());

        int pawnCount = 0;
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                Piece p = board.getPieceAt(new Position(r, c));
                if (p != null && p.getType() == 'P') pawnCount++;
            }
        }
        assertEquals(16, pawnCount);
    }
}
