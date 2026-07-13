package test.Test.unit;

import rules.PieceRules;
import models.Piece;
import models.Board;
import models.Position;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

public class PieceRulesTest {

    private final PieceRules pieceRules = new PieceRules();

    @Test
    public void testRookDirections() {
        Piece rook = new Piece('w', 'R');
        List<int[]> directions = pieceRules.getRayDirections(rook);

        // לצריח צריכים להיות 4 כיוונים ישרים
        assertEquals(4, directions.size());

        // וידוא שהכיוון {1, 0} (למטה/למעלה) קיים ברשימה
        boolean hasVertical = directions.stream().anyMatch(d -> d[0] == 1 && d[1] == 0);
        assertTrue(hasVertical);
    }

    @Test
    public void testKingOffsets() {
        Piece king = new Piece('b', 'K');
        List<int[]> offsets = pieceRules.getFixedOffsets(king);

        // למלך צריכים להיות 8 כיווני צעד סביבו
        assertEquals(8, offsets.size());
    }

    @Test
    public void testPawnValidForwardMove() {
        Board board = new Board(8, 8);
        Piece whitePawn = new Piece('w', 'P');

        // בדיקת צעד אחד קדימה של חייל לבן (מ שורה 6 לשורה 5)
        Position src = new Position(6, 3);
        Position dest = new Position(5, 3);

        var validation = pieceRules.validatePawnMove(whitePawn, src, dest, board);
        assertTrue(validation.isValid());
    }

    @Test
    public void testPawnInvalidMove() {
        Board board = new Board(8, 8);
        Piece whitePawn = new Piece('w', 'P');

        // ניסיון להזיז חייל לבן אחורה (לא חוקי)
        Position src = new Position(6, 3);
        Position dest = new Position(7, 3);

        var validation = pieceRules.validatePawnMove(whitePawn, src, dest, board);
        assertFalse(validation.isValid());
    }
}