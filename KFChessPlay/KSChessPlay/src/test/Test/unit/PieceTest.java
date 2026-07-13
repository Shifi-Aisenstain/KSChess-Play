package test.Test.unit;

import models.Piece;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class PieceTest {

    @Test
    public void testPieceInitialization() {
        Piece whiteKing = new Piece('w', 'K');

        assertEquals('w', whiteKing.getColor(), "Color should be white");
        assertEquals('K', whiteKing.getType(), "Type should be King");
    }

    @Test
    public void testPieceBlackKnight() {
        Piece blackKnight = new Piece('b', 'N');

        assertEquals('b', blackKnight.getColor(), "Color should be black");
        assertEquals('N', blackKnight.getType(), "Type should be Knight");
    }
}