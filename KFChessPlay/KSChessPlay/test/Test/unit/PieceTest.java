package test.Test.unit;

import models.Piece;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class PieceTest {

    @Test
    public void testWhiteKing() {
        Piece p = new Piece('w', 'K');
        assertEquals('w', p.getColor());
        assertEquals('K', p.getType());
    }

    @Test
    public void testBlackKnight() {
        Piece p = new Piece('b', 'N');
        assertEquals('b', p.getColor());
        assertEquals('N', p.getType());
    }

    @Test
    public void testAllPieceTypes() {
        char[] types = {'R', 'N', 'B', 'Q', 'K', 'P'};
        for (char type : types) {
            Piece w = new Piece('w', type);
            Piece b = new Piece('b', type);
            assertEquals(type, w.getType());
            assertEquals(type, b.getType());
            assertEquals('w', w.getColor());
            assertEquals('b', b.getColor());
        }
    }
}
