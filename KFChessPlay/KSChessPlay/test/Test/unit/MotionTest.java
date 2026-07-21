package test.Test.unit;

import realtime.Motion;
import models.Piece;
import models.Position;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class MotionTest {

    @Test
    public void testMotionInitialization() {
        Piece piece = new Piece('w', 'R');
        Position src = new Position(0, 0);
        Position dest = new Position(0, 2);

        // יצירת אובייקט בהנחה שיש לו בנאי בסיסי.
        // אם ה-IDE מציג אדום על השורה הזו, פשוט תמחקי את הפרמטרים בתוך ה-new Motion() שיתאימו לבנאי שלך.
        Motion motion = new Motion(piece, src, dest, 2000);
        assertNotNull(motion);
    }
}