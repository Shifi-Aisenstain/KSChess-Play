package Test.unit;

import engine.MoveEvent;
import engine.GameManager;
import models.Board;
import models.Piece;
import models.Position;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import static org.junit.jupiter.api.Assertions.*;

public class MoveEventTest {

    @Test
    public void testMoveEventGettersAndExecution() {
        Piece piece = new Piece('w', 'R');
        Position src = new Position(4, 4);
        Position dest = new Position(4, 5);
        long endTime = System.currentTimeMillis() + 1000;

        MoveEvent event = new MoveEvent(piece, src, dest, endTime);

        assertEquals(dest, event.getToPosition());
        assertEquals(1, event.getPriority());

        Board board = new Board(8, 8);
        GameManager gameManager = new GameManager();
        gameManager.initializeBoard(8, 8);
        gameManager.addPieceToBoard(4, 4, piece);

        event.execute(board, new ArrayList<>(), gameManager);

        assertEquals(piece, gameManager.getBoard().getPieceAt(dest));
    }

    @Test
    public void testPawnPromotion() {

        Piece pawn = new Piece('w', 'P');
        Position src = new Position(1, 0);
        Position dest = new Position(0, 0);
        long endTime = System.currentTimeMillis() + 1000;

        MoveEvent event = new MoveEvent(pawn, src, dest, endTime);

        Board board = new Board(8, 8);
        GameManager gameManager = new GameManager();
        gameManager.initializeBoard(8, 8);
        gameManager.addPieceToBoard(1, 0, pawn);

        event.execute(board, new ArrayList<>(), gameManager);

        Piece promotedPiece = gameManager.getBoard().getPieceAt(dest);
        assertNotNull(promotedPiece);
        assertEquals('Q', promotedPiece.getType());
    }
}
