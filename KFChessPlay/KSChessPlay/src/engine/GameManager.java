package engine;

import controller.MoveCommand;
import models.Board;
import models.Piece;
import models.Position;
import rules.RuleEngine;
import realtime.RealTimeArbiter;

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