package Test.unit;

import input.CoordinateParser;
import models.Position;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class CoordinateParserTest {

    @Test
    public void testParseValidClick() {

        Position pos = CoordinateParser.parseClick(150, 250, 8, 8);

        assertNotNull(pos);
        assertEquals(2, pos.getRow());
        assertEquals(1, pos.getCol());
    }

    @Test
    public void testParseClickOutOfBounds() {

        Position pos = CoordinateParser.parseClick(500, 100, 3, 3);

        assertNull(pos);
    }

    @Test
    public void testNegativeCoordinatesReturnNull() {

        assertNull(CoordinateParser.parseClick(-150, 50, 8, 8));
        assertNull(CoordinateParser.parseClick(50, -150, 8, 8));
    }

    @Test
    public void testRowOutOfBoundsReturnsNull() {
        assertNull(CoordinateParser.parseClick(50, 900, 8, 8));
    }

    @Test
    public void testClickOnLastValidCellIsAccepted() {
        Position pos = CoordinateParser.parseClick(799, 799, 8, 8);
        assertNotNull(pos);
        assertEquals(7, pos.getRow());
        assertEquals(7, pos.getCol());
    }
}
