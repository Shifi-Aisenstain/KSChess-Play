package test.Test.unit;

import input.CoordinateParser;
import models.Position;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class CoordinateParserTest {

    @Test
    public void testParseValidClick() {
        // בדיקת לחיצה תקינה: x=150 (עמודה 1), y=250 (שורה 2) בלוח של 8x8
        Position pos = CoordinateParser.parseClick(150, 250, 8, 8);

        assertNotNull(pos);
        assertEquals(2, pos.getRow());
        assertEquals(1, pos.getCol());
    }

    @Test
    public void testParseClickOutOfBounds() {
        // בדיקת לחיצה מחוץ לגבולות הלוח (למשל x רחוק מדי עבור לוח 3x3)
        Position pos = CoordinateParser.parseClick(500, 100, 3, 3);

        // הלוגיקה שלך צריכה להחזיר null במקרה כזה
        assertNull(pos);
    }
}