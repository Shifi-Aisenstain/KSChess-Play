package test.Test.unit;

import controller.GameController;
import engine.GameManager;
import models.Piece;
import models.Position;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class ControllerTest {

    private GameManager gm;
    private GameController controller;

    @BeforeEach
    public void setUp() {
        gm = new GameManager();
        gm.initializeBoard(8, 8);
        controller = new GameController(gm);
    }

    @Test
    public void testInitialSelectedPositionIsNull() {
        assertNull(controller.getSelectedPosition());
    }

    @Test
    public void testSelectPiece() {
        gm.addPieceToBoard(3, 3, new Piece('w', 'R'));
        controller.handleInput(3, 3);
        assertEquals(new Position(3, 3), controller.getSelectedPosition());
    }

    @Test
    public void testClickEmptySquareDoesNotSelect() {
        controller.handleInput(3, 3);
        assertNull(controller.getSelectedPosition());
    }

    @Test
    public void testClickSameSquareDeselects() {
        gm.addPieceToBoard(3, 3, new Piece('w', 'R'));
        controller.handleInput(3, 3);
        controller.handleInput(3, 3);
        assertNull(controller.getSelectedPosition());
    }

    @Test
    public void testClickFriendlyPieceReselects() {
        gm.addPieceToBoard(3, 3, new Piece('w', 'R'));
        gm.addPieceToBoard(3, 5, new Piece('w', 'N'));
        controller.handleInput(3, 3);
        controller.handleInput(3, 5);
        assertEquals(new Position(3, 5), controller.getSelectedPosition());
    }

    @Test
    public void testMoveToEmptySquare() {
        gm.addPieceToBoard(0, 0, new Piece('w', 'R'));
        controller.handleInput(0, 0);
        controller.handleInput(0, 5);
        assertNull(controller.getSelectedPosition());
        assertTrue(gm.isPieceBusy(0, 0));
    }

    @Test
    public void testMoveToEnemySquare() {
        gm.addPieceToBoard(0, 0, new Piece('w', 'R'));
        gm.addPieceToBoard(0, 3, new Piece('b', 'P'));
        controller.handleInput(0, 0);
        controller.handleInput(0, 3);
        assertNull(controller.getSelectedPosition());
        assertTrue(gm.isPieceBusy(0, 0));
    }

    @Test
    public void testRequestJump() {
        gm.addPieceToBoard(4, 4, new Piece('w', 'K'));
        controller.requestJump(4, 4);
        assertTrue(gm.isPieceBusy(4, 4));
    }

    @Test
    public void testReset() {
        gm.addPieceToBoard(3, 3, new Piece('w', 'R'));
        controller.handleInput(3, 3);
        assertNotNull(controller.getSelectedPosition());
        controller.reset();
        assertNull(controller.getSelectedPosition());
    }
}
