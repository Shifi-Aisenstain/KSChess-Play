package test.Test.integration;

import engine.GameManager;
import controller.MoveCommand;
import models.Piece;
import models.Position;
import io.BoardParser;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class ChessGameIntegrationTest {

    @Test
    public void test01BoardParsing() {
        // מדמה את 01_board_parsing.kfc
        BoardParser parser = new BoardParser();
        String mockKfcScript = "Board:\nwR . wK\n. . .\n";
        var board = parser.parseFromString(mockKfcScript);
        assertNotNull(board);
        assertEquals(2, board.getLength());
    }

    @Test
    public void test02ClickToMove() {
        // מדמה את 02_click_to_move.kfc
        GameManager gameManager = new GameManager();
        gameManager.initializeBoard(3, 3);
        Piece rook = new Piece('w', 'R');
        gameManager.addPieceToBoard(0, 0, rook);

        MoveCommand command = new MoveCommand(new Position(0, 0), new Position(0, 2));
        gameManager.requestMove(command);
        assertTrue(gameManager.isPieceBusy(0, 0));
    }

    @Test
    public void test03RookMoves() {
        // מדמה את 03_rook_moves.kfc - תנועה ארוכה של צריח בזמן אמת וסיומה
        GameManager gameManager = new GameManager();
        gameManager.initializeBoard(4, 4);
        Piece rook = new Piece('w', 'R');
        gameManager.addPieceToBoard(0, 0, rook);

        gameManager.requestMove(new MoveCommand(new Position(0, 0), new Position(0, 3)));
        gameManager.handleWait(3000); // המתנה לסיום המהלך בזמן אמת
        assertEquals(rook, gameManager.getBoard().getPieceAt(new Position(0, 3)));
    }

    @Test
    public void test04InvalidMoves() {
        // מדמה את 04_invalid_moves.kfc - ניסיון לבצע מהלך לא חוקי
        GameManager gameManager = new GameManager();
        gameManager.initializeBoard(3, 3);
        Piece rook = new Piece('w', 'R');
        gameManager.addPieceToBoard(0, 0, rook);

        // מהלך אלכסוני לצריח - אסור
        MoveCommand command = new MoveCommand(new Position(0, 0), new Position(1, 1));
        gameManager.requestMove(command);
        assertFalse(gameManager.isPieceBusy(0, 0)); // המהלך נדחה מייד
    }

    @Test
    public void test05Capture() {
        // מדמה את 05_capture.kfc - אכילת כלי אויב
        GameManager gameManager = new GameManager();
        gameManager.initializeBoard(3, 3);
        Piece whiteRook = new Piece('w', 'R');
        Piece blackPawn = new Piece('b', 'P');

        gameManager.addPieceToBoard(0, 0, whiteRook);
        gameManager.addPieceToBoard(0, 1, blackPawn);

        gameManager.requestMove(new MoveCommand(new Position(0, 0), new Position(0, 1)));
        gameManager.handleWait(1000);
        assertEquals(whiteRook, gameManager.getBoard().getPieceAt(new Position(0, 1)));
    }

    @Test
    public void test06GameOver() {
        // מדמה את 06_game_over.kfc - בדיקת סיום משחק כשהמלך מוכה
        GameManager gameManager = new GameManager();
        gameManager.initializeBoard(3, 3);
        Piece whiteRook = new Piece('w', 'R');
        Piece blackKing = new Piece('b', 'K');

        gameManager.addPieceToBoard(0, 0, whiteRook);
        gameManager.addPieceToBoard(0, 2, blackKing);

        gameManager.requestMove(new MoveCommand(new Position(0, 0), new Position(0, 2)));
        gameManager.handleWait(2000);

        // וידוא שהמלך נעלם והמשחק הסתיים
        assertNull(gameManager.getBoard().getPieceAt(new Position(0, 0)));
        assertEquals(whiteRook, gameManager.getBoard().getPieceAt(new Position(0, 2)));
    }
}