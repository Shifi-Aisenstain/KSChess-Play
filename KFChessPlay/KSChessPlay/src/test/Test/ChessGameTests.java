//package test.test;
//
//import org.junit.jupiter.api.Test;
//import org.junit.jupiter.api.DisplayName;
//import org.junit.jupiter.api.BeforeEach;
//import static org.junit.jupiter.api.Assertions.*;
//
//import engine.GameManager;
//import models.Board;
//import models.Piece;
//import models.Position;
//import input.InteractionManager;
//import rules.RuleEngine;
//import rules.MoveValidation;
//import controller.MoveCommand;
//
///**
// * ✅ Comprehensive Chess Game Test Suite
// *
// * Tests organized by category:
// * 1. Board Parsing & Validation
// * 2. Coordinate Parsing
// * 3. Piece Movement Rules
// * 4. Blocking & Capture
// * 5. Timing & Real-time Mechanics
// * 6. Jump & Air Capture
//// * 7. Game State
// */
//@DisplayName("KSChess Game - Complete Test Suite")
//public class ChessGameTests {
//
//    private GameManager gameManager;
//    private RuleEngine ruleEngine;
//    private InteractionManager interactionManager;
//
//    @BeforeEach
//    void setup() {
//        gameManager = new GameManager();
//        ruleEngine = new RuleEngine();
//        interactionManager = new InteractionManager(gameManager);
//    }
//
//    // ========== BOARD PARSING & VALIDATION ==========
//
//    @Test
//    @DisplayName("Parse empty 3x3 board")
//    void testParseEmptyBoard() {
//        gameManager.initializeBoard(3, 3);
//        String[][] board = gameManager.getUpdatedBoardMatrix();
//
//        assertEquals(3, board.length);
//        assertEquals(3, board[0].length);
//        for (int i = 0; i < 3; i++) {
//            for (int j = 0; j < 3; j++) {
//                assertEquals(".", board[i][j]);
//            }
//        }
//    }
//
//    @Test
//    @DisplayName("Parse rectangular 3x4 board with pieces")
//    void testParseRectangularBoard() {
//        gameManager.initializeBoard(3, 4);
//        gameManager.addPieceToBoard(0, 0, new Piece('w', 'K'));
//        gameManager.addPieceToBoard(0, 3, new Piece('b', 'K'));
//        gameManager.addPieceToBoard(2, 0, new Piece('w', 'R'));
//        gameManager.addPieceToBoard(2, 3, new Piece('b', 'R'));
//
//        String[][] board = gameManager.getUpdatedBoardMatrix();
//        assertEquals("wK", board[0][0]);
//        assertEquals(".", board[0][1]);
//        assertEquals("bK", board[0][3]);
//    }
//
//    // ========== COORDINATE PARSING ==========
//
//    @Test
//    @DisplayName("Select piece by center click (50,50) → (0,0)")
//    void testSelectPieceByClick() {
//        gameManager.initializeBoard(3, 3);
//        gameManager.addPieceToBoard(0, 0, new Piece('w', 'K'));
//
//        interactionManager.handleClick(50, 50); // Should select (0,0)
//        interactionManager.handleClick(150, 150); // Should try move to (1,1)
//
//        // Wait for move to complete
//        gameManager.handleWait(1000);
//
//        String[][] board = gameManager.getUpdatedBoardMatrix();
//        assertEquals(".", board[0][0]);
//        assertEquals("wK", board[1][1]);
//    }
//
//    @Test
//    @DisplayName("Click empty cell does not select")
//    void testClickEmptyCellNoSelect() {
//        gameManager.initializeBoard(3, 3);
//        gameManager.addPieceToBoard(0, 0, new Piece('w', 'K'));
//
//        interactionManager.handleClick(150, 150); // Empty cell
//        interactionManager.handleClick(250, 250); // Another empty cell
//
//        gameManager.handleWait(1000);
//
//        String[][] board = gameManager.getUpdatedBoardMatrix();
//        assertEquals("wK", board[0][0]); // King should stay
//    }
//
//    @Test
//    @DisplayName("Click outside board is ignored")
//    void testClickOutsideBoardIgnored() {
//        gameManager.initializeBoard(3, 3);
//        gameManager.addPieceToBoard(0, 0, new Piece('w', 'K'));
//
//        interactionManager.handleClick(350, 50); // Outside (x > 3*100)
//        interactionManager.handleClick(-10, 50); // Outside (x < 0)
//
//        String[][] board = gameManager.getUpdatedBoardMatrix();
//        assertEquals("wK", board[0][0]); // King should stay
//    }
//
//    // ========== PIECE MOVEMENT RULES ==========
//
//    @Test
//    @DisplayName("King one step valid")
//    void testKingOneStepValid() {
//        gameManager.initializeBoard(3, 3);
//        gameManager.addPieceToBoard(0, 0, new Piece('w', 'K'));
//
//        // Move from (0,0) to (1,1)
//        MoveCommand cmd = new MoveCommand(new Position(0, 0), new Position(1, 1));
//        MoveValidation validation = ruleEngine.validateMove(gameManager.getBoard(), new Position(0, 0), new Position(1, 1));
//
//        assertTrue(validation.isValid(), "King should move one step");
//    }
//
//    @Test
//    @DisplayName("King two steps invalid")
//    void testKingTwoStepsInvalid() {
//        gameManager.initializeBoard(3, 3);
//        gameManager.addPieceToBoard(0, 0, new Piece('w', 'K'));
//
//        MoveValidation validation = ruleEngine.validateMove(gameManager.getBoard(), new Position(0, 0), new Position(2, 2));
//
//        assertFalse(validation.isValid(), "King cannot move two steps");
//    }
//
//    @Test
//    @DisplayName("Rook straight valid")
//    void testRookStraightValid() {
//        gameManager.initializeBoard(1, 3);
//        gameManager.addPieceToBoard(0, 0, new Piece('w', 'R'));
//
//        MoveValidation validation = ruleEngine.validateMove(gameManager.getBoard(), new Position(0, 0), new Position(0, 2));
//
//        assertTrue(validation.isValid(), "Rook should move straight");
//    }
//
//    @Test
//    @DisplayName("Rook diagonal invalid")
//    void testRookDiagonalInvalid() {
//        gameManager.initializeBoard(3, 3);
//        gameManager.addPieceToBoard(0, 0, new Piece('w', 'R'));
//
//        MoveValidation validation = ruleEngine.validateMove(gameManager.getBoard(), new Position(0, 0), new Position(1, 1));
//
//        assertFalse(validation.isValid(), "Rook cannot move diagonal");
//    }
//
//    @Test
//    @DisplayName("Bishop diagonal valid")
//    void testBishopDiagonalValid() {
//        gameManager.initializeBoard(3, 3);
//        gameManager.addPieceToBoard(0, 0, new Piece('w', 'B'));
//
//        MoveValidation validation = ruleEngine.validateMove(gameManager.getBoard(), new Position(0, 0), new Position(2, 2));
//
//        assertTrue(validation.isValid(), "Bishop should move diagonal");
//    }
//
//    @Test
//    @DisplayName("Knight L-shape valid")
//    void testKnightLValid() {
//        gameManager.initializeBoard(3, 3);
//        gameManager.addPieceToBoard(0, 0, new Piece('w', 'N'));
//
//        MoveValidation validation = ruleEngine.validateMove(gameManager.getBoard(), new Position(0, 0), new Position(1, 2));
//
//        assertTrue(validation.isValid(), "Knight should move L-shape");
//    }
//
//    @Test
//    @DisplayName("Queen diagonal valid")
//    void testQueenDiagonalValid() {
//        gameManager.initializeBoard(3, 3);
//        gameManager.addPieceToBoard(0, 0, new Piece('w', 'Q'));
//
//        MoveValidation validation = ruleEngine.validateMove(gameManager.getBoard(), new Position(0, 0), new Position(2, 2));
//
//        assertTrue(validation.isValid(), "Queen should move diagonal");
//    }
//
//    // ========== BLOCKING & CAPTURE ==========
//
//    @Test
//    @DisplayName("Rook blocked by own piece")
//    void testRookBlockedByOwnPiece() {
//        gameManager.initializeBoard(1, 3);
//        gameManager.addPieceToBoard(0, 0, new Piece('w', 'R'));
//        gameManager.addPieceToBoard(0, 1, new Piece('w', 'P'));
//
//        MoveValidation validation = ruleEngine.validateMove(gameManager.getBoard(), new Position(0, 0), new Position(0, 2));
//
//        assertFalse(validation.isValid(), "Rook path blocked");
//    }
//
//    @Test
//    @DisplayName("Knight jumps over blockers")
//    void testKnightJumpsOverBlockers() {
//        gameManager.initializeBoard(3, 3);
//        gameManager.addPieceToBoard(0, 0, new Piece('w', 'N'));
//        gameManager.addPieceToBoard(0, 1, new Piece('w', 'P'));
//        gameManager.addPieceToBoard(1, 0, new Piece('w', 'P'));
//
//        MoveValidation validation = ruleEngine.validateMove(gameManager.getBoard(), new Position(0, 0), new Position(1, 2));
//
//        assertTrue(validation.isValid(), "Knight jumps over pieces");
//    }
//
//    @Test
//    @DisplayName("Cannot capture own piece")
//    void testCannotCaptureOwnPiece() {
//        gameManager.initializeBoard(1, 3);
//        gameManager.addPieceToBoard(0, 0, new Piece('w', 'R'));
//        gameManager.addPieceToBoard(0, 2, new Piece('w', 'P'));
//
//        MoveValidation validation = ruleEngine.validateMove(gameManager.getBoard(), new Position(0, 0), new Position(0, 2));
//
//        assertFalse(validation.isValid(), "Cannot capture friendly piece");
//    }
//
//    @Test
//    @DisplayName("Rook captures enemy at destination")
//    void testRookCapturesEnemy() {
//        gameManager.initializeBoard(1, 3);
//        gameManager.addPieceToBoard(0, 0, new Piece('w', 'R'));
//        gameManager.addPieceToBoard(0, 2, new Piece('b', 'R'));
//
//        MoveValidation validation = ruleEngine.validateMove(gameManager.getBoard(), new Position(0, 0), new Position(0, 2));
//
//        assertTrue(validation.isValid(), "Rook captures enemy");
//    }
//
//    // ========== PAWN SPECIAL MOVES ==========
//
//    @Test
//    @DisplayName("White pawn double from start valid")
//    void testWhitePawnDoubleFromStartValid() {
//        gameManager.initializeBoard(4, 3);
//        gameManager.addPieceToBoard(3, 1, new Piece('w', 'P'));
//
//        MoveValidation validation = ruleEngine.validateMove(gameManager.getBoard(), new Position(3, 1), new Position(1, 1));
//
//        assertTrue(validation.isValid(), "White pawn can double from start");
//    }
//
//    @Test
//    @DisplayName("Black pawn double from start valid")
//    void testBlackPawnDoubleFromStartValid() {
//        gameManager.initializeBoard(4, 3);
//        gameManager.addPieceToBoard(0, 1, new Piece('b', 'P'));
//
//        MoveValidation validation = ruleEngine.validateMove(gameManager.getBoard(), new Position(0, 1), new Position(2, 1));
//
//        assertTrue(validation.isValid(), "Black pawn can double from start");
//    }
//
//    @Test
//    @DisplayName("Pawn cannot capture forward")
//    void testPawnCannotCaptureForward() {
//        gameManager.initializeBoard(3, 3);
//        gameManager.addPieceToBoard(1, 1, new Piece('w', 'P'));
//        gameManager.addPieceToBoard(0, 1, new Piece('b', 'R'));
//
//        MoveValidation validation = ruleEngine.validateMove(gameManager.getBoard(), new Position(1, 1), new Position(0, 1));
//
//        assertFalse(validation.isValid(), "Pawn cannot capture forward");
//    }
//
//    @Test
//    @DisplayName("White pawn promotes to queen")
//    void testWhitePawnPromotes() {
//        gameManager.initializeBoard(2, 3);
//        gameManager.addPieceToBoard(1, 1, new Piece('w', 'P'));
//
//        interactionManager.handleClick(150, 150); // Select pawn
//        interactionManager.handleClick(150, 50); // Move to (0,1)
//
//        gameManager.handleWait(1000);
//
//        String[][] board = gameManager.getUpdatedBoardMatrix();
//        assertEquals("wQ", board[0][1], "Pawn should promote to Queen");
//    }
//
//    @Test
//    @DisplayName("Black pawn promotes to queen")
//    void testBlackPawnPromotes() {
//        gameManager.initializeBoard(2, 3);
//        gameManager.addPieceToBoard(0, 1, new Piece('b', 'P'));
//
//        interactionManager.handleClick(150, 50); // Select pawn
//        interactionManager.handleClick(150, 150); // Move to (1,1)
//
//        gameManager.handleWait(1000);
//
//        String[][] board = gameManager.getUpdatedBoardMatrix();
//        assertEquals("bQ", board[1][1], "Black pawn should promote to Queen");
//    }
//
//    // ========== REAL-TIME MECHANICS ==========
//
//    @Test
//    @DisplayName("One cell move - before arrival board unchanged")
//    void testOneStepBeforeArrival() {
//        gameManager.initializeBoard(1, 3);
//        gameManager.addPieceToBoard(0, 0, new Piece('w', 'R'));
//
//        interactionManager.handleClick(50, 50);
//        interactionManager.handleClick(150, 50);
//
//        gameManager.handleWait(500); // Half-way
//
//        String[][] board = gameManager.getUpdatedBoardMatrix();
//        assertEquals("wR", board[0][0], "Rook should still be at origin");
//    }
//
//    @Test
//    @DisplayName("Two cell move - before and after arrival")
//    void testTwoCellMove() {
//        gameManager.initializeBoard(1, 3);
//        gameManager.addPieceToBoard(0, 0, new Piece('w', 'R'));
//
//        interactionManager.handleClick(50, 50);
//        interactionManager.handleClick(250, 50);
//
//        gameManager.handleWait(1000);
//        String[][] board1 = gameManager.getUpdatedBoardMatrix();
//        assertEquals("wR", board1[0][2], "Rook should arrive at (0,2)");
//
//        gameManager.handleWait(1000);
//        String[][] board2 = gameManager.getUpdatedBoardMatrix();
//        assertEquals("wR", board2[0][2], "Rook should stay at (0,2)");
//    }
//
//    @Test
//    @DisplayName("Moving piece ignores redirect")
//    void testMovingPieceIgnoresRedirect() {
//        gameManager.initializeBoard(1, 3);
//        gameManager.addPieceToBoard(0, 0, new Piece('w', 'R'));
//
//        interactionManager.handleClick(50, 50);
//        interactionManager.handleClick(250, 50);
//
//        gameManager.handleWait(500);
//
//        // Try to redirect while moving
//        interactionManager.handleClick(50, 50);
//        interactionManager.handleClick(150, 50);
//
//        gameManager.handleWait(1000);
//
//        String[][] board = gameManager.getUpdatedBoardMatrix();
//        assertEquals("wR", board[0][2], "Rook should complete original move");
//    }
//
//    @Test
//    @DisplayName("Can move again after arrival without cooldown")
//    void testNoMoveCooldown() {
//        gameManager.initializeBoard(1, 3);
//        gameManager.addPieceToBoard(0, 0, new Piece('w', 'R'));
//
//        interactionManager.handleClick(50, 50);
//        interactionManager.handleClick(150, 50);
//        gameManager.handleWait(1000);
//
//        interactionManager.handleClick(150, 50);
//        interactionManager.handleClick(250, 50);
//        gameManager.handleWait(1000);
//
//        String[][] board = gameManager.getUpdatedBoardMatrix();
//        assertEquals("wR", board[0][2], "Rook should complete second move");
//    }
//
//    // ========== JUMP & AIR CAPTURE ==========
//
//    @Test
//    @DisplayName("Jump lands same square")
//    void testJumpLandsSameSquare() {
//        gameManager.initializeBoard(3, 3);
//        gameManager.addPieceToBoard(1, 1, new Piece('w', 'K'));
//
//        interactionManager.handleJump(150, 150);
//        gameManager.handleWait(1000);
//
//        String[][] board = gameManager.getUpdatedBoardMatrix();
//        assertEquals("wK", board[1][1], "King should return after jump");
//    }
//
//    @Test
//    @DisplayName("Airborne piece captures arriving enemy")
//    void testAirborneCaptures() {
//        gameManager.initializeBoard(2, 3);
//        gameManager.addPieceToBoard(1, 0, new Piece('w', 'K'));
//        gameManager.addPieceToBoard(1, 1, new Piece('b', 'R'));
//
//        interactionManager.handleJump(50, 150); // King jumps
//        interactionManager.handleClick(150, 150); // Select Rook
//        interactionManager.handleClick(50, 150); // Move Rook to King's square
//
//        gameManager.handleWait(1000);
//
//        String[][] board = gameManager.getUpdatedBoardMatrix();
//        assertEquals("wK", board[1][0], "King airborne captures Rook");
//    }
//
//    @Test
//    @DisplayName("Jump too late does not save piece")
//    void testJumpTooLate() {
//        gameManager.initializeBoard(2, 3);
//        gameManager.addPieceToBoard(1, 0, new Piece('w', 'K'));
//        gameManager.addPieceToBoard(1, 1, new Piece('b', 'R'));
//
//        interactionManager.handleClick(150, 150); // Select Rook
//        interactionManager.handleClick(50, 150); // Move to King's position
//
//        gameManager.handleWait(1000); // Wait for Rook to arrive
//
//        interactionManager.handleJump(50, 150); // Try to jump AFTER Rook arrived
//
//        String[][] board = gameManager.getUpdatedBoardMatrix();
//        assertEquals("bR", board[1][0], "Rook captures King normally");
//    }
//
//    @Test
//    @DisplayName("Cannot jump while moving")
//    void testCannotJumpWhileMoving() {
//        gameManager.initializeBoard(1, 3);
//        gameManager.addPieceToBoard(0, 0, new Piece('w', 'R'));
//
//        interactionManager.handleClick(50, 50);
//        interactionManager.handleClick(250, 50);
//
//        gameManager.handleWait(500); // Mid-move
//
//        interactionManager.handleJump(50, 50); // Try to jump the same piece
//
//        gameManager.handleWait(1500);
//
//        String[][] board = gameManager.getUpdatedBoardMatrix();
//        assertEquals("wR", board[0][2], "Rook should complete move unaffected");
//    }
//
//    // ========== GAME STATE ==========
//
//    @Test
//    @DisplayName("King capture ends game")
//    void testKingCaptureEndsGame() {
//        gameManager.initializeBoard(1, 3);
//        gameManager.addPieceToBoard(0, 0, new Piece('w', 'R'));
//        gameManager.addPieceToBoard(0, 2, new Piece('b', 'K'));
//
//        interactionManager.handleClick(50, 50);
//        interactionManager.handleClick(250, 50);
//
//        gameManager.handleWait(2000);
//
//        assertTrue(gameManager.isGameOver(), "Game should be over after King capture");
//    }
//
//    @Test
//    @DisplayName("No moves after game over")
//    void testNoMovesAfterGameOver() {
//        gameManager.initializeBoard(2, 3);
//        gameManager.addPieceToBoard(0, 0, new Piece('w', 'R'));
//        gameManager.addPieceToBoard(0, 2, new Piece('b', 'K'));
//        gameManager.addPieceToBoard(1, 0, new Piece('b', 'R'));
//
//        interactionManager.handleClick(50, 50);
//        interactionManager.handleClick(250, 50);
//        gameManager.handleWait(2000);
//
//        // Try to move after game over
//        interactionManager.handleClick(50, 150);
//        interactionManager.handleClick(150, 150);
//        gameManager.handleWait(1000);
//
//        String[][] board = gameManager.getUpdatedBoardMatrix();
//        assertEquals("bR", board[1][0], "Black Rook should not move");
//    }
//}
