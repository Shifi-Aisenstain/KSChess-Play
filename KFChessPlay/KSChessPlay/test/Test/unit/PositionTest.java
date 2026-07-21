package test.Test.unit;

import models.Position;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class PositionTest {

    @Test
    public void testGetters() {
        Position p = new Position(5, 7);
        assertEquals(5, p.getRow());
        assertEquals(7, p.getCol());
    }

    @Test
    public void testEqualsSameValues() {
        Position p1 = new Position(2, 3);
        Position p2 = new Position(2, 3);
        assertEquals(p1, p2);
    }

    @Test
    public void testEqualsDifferentValues() {
        assertNotEquals(new Position(2, 3), new Position(0, 1));
        assertNotEquals(new Position(2, 3), new Position(2, 4));
        assertNotEquals(new Position(2, 3), new Position(3, 3));
    }

    @Test
    public void testEqualsNull() {
        assertNotEquals(new Position(1, 1), null);
    }

    @Test
    public void testEqualsWrongType() {
        assertNotEquals(new Position(1, 1), "not a position");
    }

    @Test
    public void testEqualsSameReference() {
        Position p = new Position(3, 3);
        assertEquals(p, p);
    }

    @Test
    public void testHashCodeContract() {
        Position p1 = new Position(2, 3);
        Position p2 = new Position(2, 3);
        assertEquals(p1.hashCode(), p2.hashCode());
    }

    @Test
    public void testHashCodeDifferent() {
        assertNotEquals(new Position(0, 0).hashCode(), new Position(1, 0).hashCode());
    }
}
