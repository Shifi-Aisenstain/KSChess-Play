package test.Test.unit;

import rules.MoveValidation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class MoveValidationTest {

    @Test
    public void testValidResult() {
        MoveValidation v = MoveValidation.valid();
        assertTrue(v.isValid());
        assertEquals("", v.getMessage());
        assertEquals("✓ Valid", v.toString());
    }

    @Test
    public void testInvalidResult() {
        MoveValidation v = MoveValidation.invalid("out of bounds");
        assertFalse(v.isValid());
        assertEquals("out of bounds", v.getMessage());
        assertEquals("✗ Invalid: out of bounds", v.toString());
    }

    @Test
    public void testInvalidWithEmptyReason() {
        MoveValidation v = MoveValidation.invalid("");
        assertFalse(v.isValid());
        assertEquals("", v.getMessage());
    }
}
