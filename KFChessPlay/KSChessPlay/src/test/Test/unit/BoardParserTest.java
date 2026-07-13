package test.Test.unit;

import io.BoardParser;
import models.Board;
import models.Position;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class BoardParserTest {

    private final BoardParser parser = new BoardParser();

    @Test
    public void testParseValidInput() {
        String data = "Board:\nwR . wK\n. . .\n";
        Board board = parser.parseFromString(data);

        assertNotNull(board);
        assertEquals(2, board.getLength());
        assertEquals(3, board.getCols());
        assertEquals('R', board.getPieceAt(new Position(0, 0)).getType());
    }

    @Test
    public void testParseInvalidDimensions() {
        String badData = "wR . wK\n. .\n"; // חוסר התאמה במספר איברים בשורה
        assertNull(parser.parseFromString(badData));
    }
}