package test.Test.unit;

import engine.*;
import models.Board;
import models.Piece;
import models.Position;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

public class GameEventTest {

    private static class StubEvent extends GameEvent {
        public StubEvent(Piece piece, Position from, long endTime) {
            super(piece, from, endTime);
        }
        @Override public int getPriority() { return 99; }
        @Override public void execute(Board board, List<GameEvent> events, GameManager gm) {}
    }

    @Test
    public void testGameEventGetters() {
        Piece piece = new Piece('w', 'R');
        Position pos = new Position(1, 2);
        long end = 5000L;
        StubEvent e = new StubEvent(piece, pos, end);
        assertEquals(piece, e.getPiece());
        assertEquals(pos, e.getFromPosition());
        assertEquals(end, e.getEndTime());
        assertEquals(99, e.getPriority());
    }

    @Test
    public void testMoveEventPriorityAndGetter() {
        Piece piece = new Piece('w', 'R');
        Position src = new Position(0, 0);
        Position dest = new Position(0, 3);
        MoveEvent e = new MoveEvent(piece, src, dest, 3000L);
        assertEquals(1, e.getPriority());
        assertEquals(dest, e.getToPosition());
        assertEquals(src, e.getFromPosition());
    }

    @Test
    public void testJumpEventPriority() {
        JumpEvent e = new JumpEvent(new Piece('b', 'K'), new Position(3, 3), 1000L);
        assertEquals(2, e.getPriority());
    }

    @Test
    public void testCooldownEventPriorityAndType() {
        Piece piece = new Piece('w', 'R');
        Position pos = new Position(0, 0);
        CooldownEvent longRest = new CooldownEvent(piece, pos, 3000L, CooldownEvent.Type.LONG_REST);
        CooldownEvent shortRest = new CooldownEvent(piece, pos, 2000L, CooldownEvent.Type.SHORT_REST);
        assertEquals(3, longRest.getPriority());
        assertEquals(3, shortRest.getPriority());
        assertEquals(CooldownEvent.Type.LONG_REST, longRest.getCooldownType());
        assertEquals(CooldownEvent.Type.SHORT_REST, shortRest.getCooldownType());
    }

    @Test
    public void testCooldownEventExecuteDoesNothing() {
        GameManager gm = new GameManager();
        gm.initializeBoard(8, 8);
        Board board = gm.getBoard();
        CooldownEvent e = new CooldownEvent(new Piece('w', 'R'), new Position(0, 0), 1000L, CooldownEvent.Type.LONG_REST);
        e.execute(board, new ArrayList<>(), gm);
    }

    @Test
    public void testJumpEventExecuteRestoresPiece() {
        GameManager gm = new GameManager();
        gm.initializeBoard(8, 8);
        Board board = gm.getBoard();
        Piece king = new Piece('w', 'K');
        Position pos = new Position(4, 4);
        JumpEvent e = new JumpEvent(king, pos, 1000L);
        e.execute(board, new ArrayList<>(), gm);
        assertEquals(king, board.getPieceAt(pos));
    }

    @Test
    public void testMoveEventExecuteMoviesPiece() {
        GameManager gm = new GameManager();
        gm.initializeBoard(8, 8);
        Piece rook = new Piece('w', 'R');
        gm.addPieceToBoard(0, 0, rook);
        MoveEvent e = new MoveEvent(rook, new Position(0, 0), new Position(0, 3), 3000L);
        e.execute(gm.getBoard(), new ArrayList<>(), gm);
        assertEquals(rook, gm.getBoard().getPieceAt(new Position(0, 3)));
        assertNull(gm.getBoard().getPieceAt(new Position(0, 0)));
    }

    @Test
    public void testMoveEventBlockedByFriendly() {
        GameManager gm = new GameManager();
        gm.initializeBoard(8, 8);
        Piece rook = new Piece('w', 'R');
        Piece friendly = new Piece('w', 'N');
        gm.addPieceToBoard(0, 0, rook);
        gm.addPieceToBoard(0, 3, friendly);
        MoveEvent e = new MoveEvent(rook, new Position(0, 0), new Position(0, 5), 5000L);
        e.execute(gm.getBoard(), new ArrayList<>(), gm);
        assertNotNull(gm.getBoard().getPieceAt(new Position(0, 0)));
    }

    @Test
    public void testMoveEventCapturesEnemy() {
        GameManager gm = new GameManager();
        gm.initializeBoard(8, 8);
        Piece rook = new Piece('w', 'R');
        Piece enemy = new Piece('b', 'P');
        gm.addPieceToBoard(0, 0, rook);
        gm.addPieceToBoard(0, 2, enemy);
        MoveEvent e = new MoveEvent(rook, new Position(0, 0), new Position(0, 5), 5000L);
        e.execute(gm.getBoard(), new ArrayList<>(), gm);
        assertEquals(rook, gm.getBoard().getPieceAt(new Position(0, 2)));
    }

    @Test
    public void testMoveEventCapturedByJumper() {
        GameManager gm = new GameManager();
        gm.initializeBoard(8, 8);
        Piece rook = new Piece('w', 'R');
        Piece jumper = new Piece('b', 'K');
        gm.addPieceToBoard(0, 0, rook);
        Position dest = new Position(0, 3);
        MoveEvent move = new MoveEvent(rook, new Position(0, 0), dest, 3000L);
        JumpEvent jump = new JumpEvent(jumper, dest, 3000L);
        List<GameEvent> events = new ArrayList<>();
        events.add(jump);
        move.execute(gm.getBoard(), events, gm);
        assertNull(gm.getBoard().getPieceAt(new Position(0, 0)));
        assertNull(gm.getBoard().getPieceAt(dest));
    }

    @Test
    public void testPawnPromotionWhite() {
        GameManager gm = new GameManager();
        gm.initializeBoard(8, 8);
        Piece pawn = new Piece('w', 'P');
        gm.addPieceToBoard(1, 0, pawn);
        MoveEvent e = new MoveEvent(pawn, new Position(1, 0), new Position(0, 0), 1000L);
        e.execute(gm.getBoard(), new ArrayList<>(), gm);
        assertEquals('Q', gm.getBoard().getPieceAt(new Position(0, 0)).getType());
    }

    @Test
    public void testPawnPromotionBlack() {
        GameManager gm = new GameManager();
        gm.initializeBoard(8, 8);
        Piece pawn = new Piece('b', 'P');
        gm.addPieceToBoard(6, 0, pawn);
        MoveEvent e = new MoveEvent(pawn, new Position(6, 0), new Position(7, 0), 1000L);
        e.execute(gm.getBoard(), new ArrayList<>(), gm);
        assertEquals('Q', gm.getBoard().getPieceAt(new Position(7, 0)).getType());
    }
}
