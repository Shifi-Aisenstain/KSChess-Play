package test.Test.unit;

import engine.GameManager;
import controller.MoveCommand;
import models.Piece;
import models.Position;
import view.GameSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.Collections;
import static org.junit.jupiter.api.Assertions.*;

public class GameManagerTest {

    private GameManager gm;

    @BeforeEach
    public void setUp() {
        gm = new GameManager();
        gm.initializeBoard(8, 8);
    }

    @Test
    public void testInitializeBoard() {
        assertNotNull(gm.getBoard());
        assertEquals(8, gm.getBoard().getLength());
        assertEquals(8, gm.getBoard().getCols());
    }

    @Test
    public void testAddPieceToBoard() {
        gm.addPieceToBoard(0, 0, new Piece('w', 'R'));
        assertNotNull(gm.getBoard().getPieceAt(new Position(0, 0)));
    }

    @Test
    public void testAddPieceToBoardWhenBoardNull() {
        GameManager empty = new GameManager();
        empty.addPieceToBoard(0, 0, new Piece('w', 'R'));
    }

    @Test
    public void testHasPieceAt() {
        gm.addPieceToBoard(3, 3, new Piece('w', 'K'));
        assertTrue(gm.hasPieceAt(new Position(3, 3)));
        assertFalse(gm.hasPieceAt(new Position(0, 0)));
    }

    @Test
    public void testHasPieceAtNullBoard() {
        GameManager empty = new GameManager();
        assertFalse(empty.hasPieceAt(new Position(0, 0)));
    }

    @Test
    public void testSameColorAt() {
        gm.addPieceToBoard(0, 0, new Piece('w', 'R'));
        gm.addPieceToBoard(0, 1, new Piece('w', 'N'));
        gm.addPieceToBoard(0, 2, new Piece('b', 'R'));
        assertTrue(gm.sameColorAt(new Position(0, 0), new Position(0, 1)));
        assertFalse(gm.sameColorAt(new Position(0, 0), new Position(0, 2)));
    }

    @Test
    public void testSameColorAtNullBoard() {
        GameManager empty = new GameManager();
        assertFalse(empty.sameColorAt(new Position(0, 0), new Position(0, 1)));
    }

    @Test
    public void testSameColorAtMissingPiece() {
        gm.addPieceToBoard(0, 0, new Piece('w', 'R'));
        assertFalse(gm.sameColorAt(new Position(0, 0), new Position(1, 1)));
    }

    @Test
    public void testRequestMoveRegistersAsBusy() {
        gm.addPieceToBoard(0, 0, new Piece('w', 'R'));
        gm.requestMove(new MoveCommand(new Position(0, 0), new Position(0, 5)));
        assertTrue(gm.isPieceBusy(0, 0));
    }

    @Test
    public void testRequestMoveInvalidIgnored() {
        gm.addPieceToBoard(0, 0, new Piece('w', 'R'));
        gm.requestMove(new MoveCommand(new Position(0, 0), new Position(3, 3)));
        assertFalse(gm.isPieceBusy(0, 0));
    }

    @Test
    public void testRequestMoveWhenGameOver() {
        gm.addPieceToBoard(0, 0, new Piece('w', 'R'));
        gm.setGameOver(true);
        gm.requestMove(new MoveCommand(new Position(0, 0), new Position(0, 5)));
        assertFalse(gm.isPieceBusy(0, 0));
    }

    @Test
    public void testRequestMoveBusyPieceIgnored() {
        gm.addPieceToBoard(0, 0, new Piece('w', 'R'));
        gm.requestMove(new MoveCommand(new Position(0, 0), new Position(0, 5)));
        gm.requestMove(new MoveCommand(new Position(0, 0), new Position(0, 3)));
        assertTrue(gm.isPieceBusy(0, 0));
    }

    @Test
    public void testRequestJumpRegistersAsBusy() {
        gm.addPieceToBoard(3, 3, new Piece('w', 'K'));
        gm.requestJump(new Position(3, 3));
        assertTrue(gm.isPieceBusy(3, 3));
    }

    @Test
    public void testRequestJumpWhenGameOver() {
        gm.addPieceToBoard(3, 3, new Piece('w', 'K'));
        gm.setGameOver(true);
        gm.requestJump(new Position(3, 3));
        assertFalse(gm.isPieceBusy(3, 3));
    }

    @Test
    public void testRequestJumpBusyPieceIgnored() {
        gm.addPieceToBoard(3, 3, new Piece('w', 'K'));
        gm.requestJump(new Position(3, 3));
        gm.requestJump(new Position(3, 3));
        assertTrue(gm.isPieceBusy(3, 3));
    }

    @Test
    public void testHandleWaitCompletesMove() {
        Piece rook = new Piece('w', 'R');
        gm.addPieceToBoard(0, 0, rook);
        gm.requestMove(new MoveCommand(new Position(0, 0), new Position(0, 3)));
        gm.handleWait(3000);
        assertEquals(rook, gm.getBoard().getPieceAt(new Position(0, 3)));
        assertNull(gm.getBoard().getPieceAt(new Position(0, 0)));
    }

    @Test
    public void testCaptureKingSetsGameOver() {
        gm.addPieceToBoard(0, 0, new Piece('w', 'R'));
        gm.addPieceToBoard(0, 2, new Piece('b', 'K'));
        gm.requestMove(new MoveCommand(new Position(0, 0), new Position(0, 2)));
        gm.handleWait(2000);
        assertTrue(gm.isGameOver());
    }

    @Test
    public void testClearPosition() {
        gm.addPieceToBoard(2, 2, new Piece('w', 'R'));
        gm.clearPosition(new Position(2, 2));
        assertNull(gm.getBoard().getPieceAt(new Position(2, 2)));
    }

    @Test
    public void testClearPositionNullBoard() {
        GameManager empty = new GameManager();
        empty.clearPosition(new Position(0, 0));
    }

    @Test
    public void testGetLegalDestinations() {
        gm.addPieceToBoard(0, 0, new Piece('w', 'R'));
        assertFalse(gm.getLegalDestinations(new Position(0, 0)).isEmpty());
    }

    @Test
    public void testGetLegalDestinationsNullBoard() {
        GameManager empty = new GameManager();
        assertTrue(empty.getLegalDestinations(new Position(0, 0)).isEmpty());
    }

    @Test
    public void testGetLegalDestinationsNullSrc() {
        assertTrue(gm.getLegalDestinations(null).isEmpty());
    }

    @Test
    public void testGetUpdatedBoardMatrix() {
        gm.addPieceToBoard(0, 0, new Piece('w', 'R'));
        String[][] matrix = gm.getUpdatedBoardMatrix();
        assertEquals("wR", matrix[0][0]);
    }

    @Test
    public void testReset() {
        gm.addPieceToBoard(0, 0, new Piece('w', 'R'));
        gm.setGameOver(true);
        gm.reset();
        assertNull(gm.getBoard());
        assertFalse(gm.isGameOver());
    }

    @Test
    public void testCreateSnapshot() {
        gm.addPieceToBoard(0, 0, new Piece('w', 'R'));
        GameSnapshot snap = gm.createSnapshot();
        assertNotNull(snap);
        assertFalse(snap.isGameOver());
        assertFalse(snap.getPieces().isEmpty());
    }

    @Test
    public void testCreateSnapshotWithSelection() {
        gm.addPieceToBoard(0, 0, new Piece('w', 'R'));
        Position sel = new Position(0, 0);
        GameSnapshot snap = gm.createSnapshot(sel, gm.getLegalDestinations(sel));
        assertEquals(sel, snap.getSelectedPosition());
        assertFalse(snap.getLegalMoves().isEmpty());
    }

    @Test
    public void testSnapshotWinnerAfterKingCapture() {
        gm.addPieceToBoard(0, 0, new Piece('w', 'R'));
        gm.addPieceToBoard(0, 2, new Piece('b', 'K'));
        gm.addPieceToBoard(7, 7, new Piece('w', 'K'));
        gm.requestMove(new MoveCommand(new Position(0, 0), new Position(0, 2)));
        gm.handleWait(2000);
        GameSnapshot snap = gm.createSnapshot();
        assertTrue(snap.isGameOver());
        assertEquals("White", snap.getWinner());
    }

    @Test
    public void testSnapshotGameOverMessageWithWinner() {
        gm.addPieceToBoard(0, 0, new Piece('w', 'R'));
        gm.addPieceToBoard(0, 2, new Piece('b', 'K'));
        gm.addPieceToBoard(7, 7, new Piece('w', 'K'));
        gm.requestMove(new MoveCommand(new Position(0, 0), new Position(0, 2)));
        gm.handleWait(2000);
        GameSnapshot snap = gm.createSnapshot();
        assertEquals("Game Over — White wins!", snap.getGameOverMessage());
    }

    @Test
    public void testSnapshotScoreAfterCapture() {
        gm.addPieceToBoard(0, 0, new Piece('w', 'R'));
        gm.addPieceToBoard(0, 2, new Piece('b', 'P'));
        gm.requestMove(new MoveCommand(new Position(0, 0), new Position(0, 2)));
        gm.handleWait(2000);
        GameSnapshot snap = gm.createSnapshot();
        assertEquals(1, snap.getScoreWhite());
        assertEquals(0, snap.getScoreBlack());
    }

    @Test
    public void testSnapshotMoveHistory() {
        gm.addPieceToBoard(0, 0, new Piece('w', 'R'));
        gm.requestMove(new MoveCommand(new Position(0, 0), new Position(0, 3)));
        gm.handleWait(3000);
        GameSnapshot snap = gm.createSnapshot();
        assertEquals(1, snap.getWhiteMoveHistory().size());
        assertTrue(snap.getBlackMoveHistory().isEmpty());
    }

    @Test
    public void testSnapshotPieceInMotion() {
        gm.addPieceToBoard(0, 0, new Piece('w', 'R'));
        gm.requestMove(new MoveCommand(new Position(0, 0), new Position(0, 3)));
        gm.handleWait(1500);
        GameSnapshot snap = gm.createSnapshot();
        assertEquals("move", snap.getPieces().get(0).state);
    }

    @Test
    public void testSnapshotPieceJumping() {
        gm.addPieceToBoard(3, 3, new Piece('w', 'K'));
        gm.requestJump(new Position(3, 3));
        gm.handleWait(500);
        GameSnapshot snap = gm.createSnapshot();
        assertEquals("jump", snap.getPieces().get(0).state);
    }

    @Test
    public void testSnapshotCooldownHighlightsAfterJump() {
        gm.addPieceToBoard(3, 3, new Piece('w', 'K'));
        gm.requestJump(new Position(3, 3));
        gm.handleWait(500);
        GameSnapshot snap = gm.createSnapshot();
        assertFalse(snap.getCooldownHighlights().isEmpty());
    }

    @Test
    public void testSnapshotCooldownAfterMove() {
        gm.addPieceToBoard(0, 0, new Piece('w', 'R'));
        gm.requestMove(new MoveCommand(new Position(0, 0), new Position(0, 1)));
        gm.handleWait(1000);
        GameSnapshot snap = gm.createSnapshot();
        assertFalse(snap.getCooldownHighlights().isEmpty());
    }
}
