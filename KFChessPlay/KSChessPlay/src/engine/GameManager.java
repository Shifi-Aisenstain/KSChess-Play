package engine;

import controller.MoveCommand;
import models.Board;
import models.Piece;
import models.Position;
import rules.RuleEngine;
import realtime.RealTimeArbiter;

/**
 * ✅ GameManager: Central Orchestrator & Two-Phase Validation
 * 
 * RESPONSIBILITY: Coordinate between layers and enforce game rules.
 * - Receives move requests from InteractionManager
 * - Validates moves through RuleEngine (chess rules)
 * - Checks real-time state through Arbiter (is piece busy?)
 * - Delegates execution to Arbiter and events
 * 
 * TWO-PHASE VALIDATION PATTERN (CR Requirement Part B):
 * 
 * Phase 1: Chess Rules (RuleEngine)
 *   Question: "Is this move geometrically valid?"
 *   What it checks:
 *     - Source and destination on board?
 *     - Piece exists at source?
 *     - Move follows piece's movement rules?
 *     - Path not blocked?
 *   Returns: MoveValidation object (with detailed error message)
 * 
 * Phase 2: Real-Time State (Arbiter)
 *   Question: "Can this piece move RIGHT NOW?"
 *   What it checks:
 *     - Is piece currently in motion?
 *     - Is destination occupied by arriving piece?
 *     - Is game over?
 *   Returns: boolean isPieceBusy()
 * 
 * EXAMPLE:
 *   Rook at (0,0) trying to move to (0,3) when Bishop is in flight to (0,2):
 *   
 *   Phase 1: ✅ Valid - Rook can move right in straight line
 *   Phase 2: ❌ Invalid - Destination will be occupied when rook arrives
 *   Result: Move rejected
 * 
 * SEPARATION OF CONCERNS:
 * This pattern ensures:
 * - RuleEngine stays STATELESS (no board reference)
 * - Arbiter handles TIMING correctly
 * - No logic duplication
 * - Easy to test each layer independently
 * 
 * @author Chess Game Architecture
 * @version 1.0 (Two-Phase Validation)
 */
public class GameManager {
    private Board board;
    private final RuleEngine ruleEngine = new RuleEngine();
    private final RealTimeArbiter arbiter = new RealTimeArbiter();
    private boolean isGameOver = false;

    // בנאי ריק ומדויק - מונע שגיאות ב-Main (תואם לתמונה 4)
    public GameManager() {}

    public void initializeBoard(int rows, int cols) {
        this.board = new Board(rows, cols);
        this.arbiter.setBoard(this.board);
    }

    public void addPieceToBoard(int row, int col, Piece piece) {
        if (this.board != null) {
            this.board.setPieceAt(new Position(row, col), piece);
        }
    }

    public void requestMove(MoveCommand command) {
        if (isGameOver || board == null) return;

        var validation = ruleEngine.validateMove(board, command.getSource(), command.getDestination());
        if (!validation.isValid()) return;

        // 🔥 תיקון הקומפילציה: פירוק ל-int, int כפי שהארביטר דורש
        int row = command.getSource().getRow();
        int col = command.getSource().getCol();
        if (arbiter.isPieceBusy(row, col)) {
            return;
        }

        Piece piece = board.getPieceAt(command.getSource());
        arbiter.registerMove(piece, command.getSource(), command.getDestination());
    }

    public void requestJump(Position pos) {
        if (isGameOver || board == null) return;

        var validation = ruleEngine.validateJump(board, pos);
        if (!validation.isValid()) return;

        // 🔥 תיקון הקומפילציה: פירוק ל-int, int כפי שהארביטר דורש
        if (arbiter.isPieceBusy(pos.getRow(), pos.getCol())) {
            return;
        }

        Piece piece = board.getPieceAt(pos);
        arbiter.registerJump(piece, pos);
    }

    public void handleWait(int ms) {
        arbiter.advanceTime(ms, this);
    }

    // תמיכה בשתי החתימות עבור גמישות מול הטסטים השונים
    public boolean isPieceBusy(Position pos) {
        return arbiter.isPieceBusy(pos.getRow(), pos.getCol());
    }

    public boolean isPieceBusy(int row, int col) {
        return arbiter.isPieceBusy(row, col);
    }

    public void executeActualMove(Position src, Position dest, Piece pieceToPlace) {
        if (board == null) return;

        Piece target = board.getPieceAt(dest);
        if (target != null && target.getType() == 'K') {
            this.isGameOver = true;
        }
        board.setPieceAt(dest, pieceToPlace);
        board.setPieceAt(src, null);
    }

    public void clearPosition(Position pos) {
        if (board != null) {
            board.setPieceAt(pos, null);
        }
    }

    public void setGameOver(boolean gameOver) {
        this.isGameOver = gameOver;
    }

    public Board getBoard() { return this.board; }
    public boolean isGameOver() { return this.isGameOver; }
    public String[][] getUpdatedBoardMatrix() { return board.getReadOnlyMatrixView(); }
}