package test.Test.unit;

import rules.MoveValidation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class MoveValidationTest {

    @Test
    public void testMoveValidationSuccess() {
        // בדיקת תרחיש תקין (Valid)
        MoveValidation validResult = MoveValidation.valid();

        assertTrue(validResult.isValid());
        assertEquals("", validResult.getMessage());
        assertEquals("✓ Valid", validResult.toString());
    }

    @Test
    public void testMoveValidationFailure() {
        // בדיקת תרחיש שגוי (Invalid)
        String reason = "Piece cannot move to this position";
        MoveValidation invalidResult = MoveValidation.invalid(reason);

        assertFalse(invalidResult.isValid());
        assertEquals(reason, invalidResult.getMessage());
        assertEquals("✗ Invalid: " + reason, invalidResult.toString());
    }
}