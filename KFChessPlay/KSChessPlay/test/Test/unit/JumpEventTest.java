package Test.unit;

import engine.JumpEvent;
import engine.GameManager;
import models.Board;
import models.Piece;
import models.Position;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import static org.junit.jupiter.api.Assertions.*;

public class JumpEventTest {

    @Test
    public void testJumpEventGettersAndExecution() {
        Piece piece = new Piece('w', 'K');
        Position pos = new Position(3, 3);
        long endTime = System.currentTimeMillis() + 1000;

        JumpEvent event = new JumpEvent(piece, pos, endTime);

        assertEquals(piece, event.getPiece());
        assertEquals(pos, event.getFromPosition());
        assertEquals(endTime, event.getEndTime());
        assertEquals(2, event.getPriority());

        Board board = new Board(8, 8);
        GameManager gameManager = new GameManager();
        gameManager.initializeBoard(8, 8);

        assertNull(board.getPieceAt(pos));

        event.execute(board, new ArrayList<>(), gameManager);

        assertEquals(piece, board.getPieceAt(pos));
    }
}
