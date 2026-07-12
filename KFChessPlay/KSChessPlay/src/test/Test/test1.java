//
//import org.junit.jupiter.api.Test;
//import org.junit.jupiter.api.DisplayName;
//import static org.junit.jupiter.api.Assertions.*;
//
//@DisplayName("Chess Engine - Comprehensive Movement Tests")
//public class test1 {
//
//    @Test
//    @DisplayName("Pawn Regular Move - White pawn advances forward into empty space")
//    void testPawnRegularMove() {
//        String[][] customBoard = {
//                {".", ".", "."},
//                {".", "wP", "."},
//                {".", ".", "."}
//        };
//
//        boolean isValid = Main.testMoveOnCustomBoard(customBoard, 1, 1, 0, 1);
//        assertTrue(isValid, "White pawn should be able to move 1 step forward into an empty cell.");
//    }
//
//    @Test
//    @DisplayName("Pawn Tactical Capture - White pawn captures black pawn diagonally")
//    void testPawnDiagonalCapture() {
//        String[][] customBoard = {
//                {"wK", ".", "bP", "bK"},
//                {".", "wP", ".", "."},
//                {"wR", ".", ".", "bR"}
//        };
//
//        boolean isValid = Main.testMoveOnCustomBoard(customBoard, 1, 1, 0, 2);
//        assertTrue(isValid, "White pawn should be able to capture an enemy piece diagonally.");
//    }
//
//    @Test
//    @DisplayName("Rook Path Blocked - Rook cannot skip over another piece")
//    void testRookPathBlocked() {
//        String[][] customBoard = {
//                {"wR", "wK", ".", "."},
//                {".", ".", "."}
//        };
//
//        boolean isValid = Main.testMoveOnCustomBoard(customBoard, 0, 0, 0, 2);
//        assertFalse(isValid, "Rook should not be able to move if its path is blocked by another piece.");
//    }
//
//    @Test
//    @DisplayName("Knight Leaping - Knight can jump over other pieces")
//    void testKnightLeaping() {
//        String[][] customBoard = {
//                {"wN", "wP", "."},
//                {"bP", "bK", "."},
//                {".", ".", "."}
//        };
//
//        boolean isValid = Main.testMoveOnCustomBoard(customBoard, 0, 0, 2, 1);
//        assertTrue(isValid, "Knight should be able to leap over obstacles to complete a valid L-move.");
//    }
//}