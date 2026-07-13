package test.Test.unit;

import input.BoardMapper;
import models.Position;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class BoardMapperTest {

    private final BoardMapper mapper = new BoardMapper();

    @Test
    public void testValidStringMapping() {
        // a1 -> row=0, col=0
        Position p1 = mapper.mapStringToPosition("a1");
        assertEquals(0, p1.getRow());
        assertEquals(0, p1.getCol());

        // b3 -> row=2, col=1
        Position p2 = mapper.mapStringToPosition("b3");
        assertEquals(2, p2.getRow());
        assertEquals(1, p2.getCol());

        // בדיקת אותיות גדולות (Case Insensitivity)
        Position p3 = mapper.mapStringToPosition("A1");
        assertEquals(0, p3.getRow());
        assertEquals(0, p3.getCol());
    }

    @Test
    public void testInvalidNullOrEmptyInput() {
        assertThrows(IllegalArgumentException.class, () -> {
            mapper.mapStringToPosition(null);
        });

        assertThrows(IllegalArgumentException.class, () -> {
            mapper.mapStringToPosition("");
        });

        assertThrows(IllegalArgumentException.class, () -> {
            mapper.mapStringToPosition("a");
        });
    }
}