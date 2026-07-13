package test.Test.unit;

import models.Position;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class PositionTest {

    @Test
    public void testEqualsAndHashCodeContract() {
        Position p1 = new Position(2, 3);
        Position p2 = new Position(2, 3);
        Position p3 = new Position(0, 1);

        // בדיקת לוגיקת equals
        assertEquals(p1, p2);
        assertNotEquals(p1, p3);
        assertNotEquals(p1, null);
        assertNotEquals(p1, "not a position");

        // בדיקת חוזה ה-hashCode
        assertEquals(p1.hashCode(), p2.hashCode());
        assertNotEquals(p1.hashCode(), p3.hashCode());
    }

    @Test
    public void testGetters() {
        Position p = new Position(5, 7);
        assertEquals(5, p.getRow());
        assertEquals(7, p.getCol());
    }
}