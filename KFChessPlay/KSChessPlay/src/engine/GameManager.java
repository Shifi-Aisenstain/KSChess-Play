package engine;

import controller.MoveCommand;
import models.Board;
import models.Piece;
import models.Position;
import rules.RuleEngine;
import realtime.RealTimeArbiter;
import view.GameSnapshot;
import view.PieceSnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class GameManager {
    // How high (in tile-heights) a jumping piece rises at the peak of its
    // bounce. Purely a rendering constant - has no effect on game rules.
    private static final double JUMP_BOUNCE_HEIGHT = 0.3;

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

    /**
     * All squares `src`'s piece could legally move to right now. Used by the
     * renderer to highlight legal destinations for the currently selected
     * piece. Delegates the actual rule check to RuleEngine (stays the single
     * place that knows how pieces move) - this just enumerates candidates.
     */
    public List<Position> getLegalDestinations(Position src) {
        List<Position> result = new ArrayList<>();
        if (board == null || src == null) return result;

        for (int r = 0; r < board.getLength(); r++) {
            for (int c = 0; c < board.getCols(); c++) {
                Position dest = new Position(r, c);
                if (ruleEngine.validateMove(board, src, dest).isValid()) {
                    result.add(dest);
                }
            }
        }
        return result;
    }

    public GameSnapshot createSnapshot() {
        return createSnapshot(null, Collections.emptyList());
    }

    /**
     * Builds the per-frame render state. Beyond "where is each piece",
     * this also answers "is it currently mid-move or mid-jump, and how far
     * along is that animation" by cross-referencing the arbiter's active
     * events against the shared game clock - that's what lets the renderer
     * draw a piece sliding across the squares it passes through, or
     * bouncing during a jump, instead of only ever snapping between rest
     * positions.
     */
    public GameSnapshot createSnapshot(Position selectedPosition, List<Position> legalMoves) {
        List<PieceSnapshot> pieces = new ArrayList<>();
        long clockMs = arbiter.getClockMs();

        for (int r = 0; r < board.getLength(); r++) {
            for (int c = 0; c < board.getCols(); c++) {
                Position pos = new Position(r, c);
                Piece p = board.getPieceAt(pos);
                if (p == null) continue;

                MoveEvent activeMove = findActiveMove(pos);
                JumpEvent activeJump = findActiveJump(pos);

                double renderRow = r;
                double renderCol = c;
                String state = "idle";

                if (activeMove != null) {
                    Position dest = activeMove.getToPosition();
                    int distance = Math.max(
                            Math.abs(dest.getRow() - r),
                            Math.abs(dest.getCol() - c));
                    long duration = Math.max(1L, distance * RealTimeArbiter.TIME_PER_CELL_MS);
                    long startTime = activeMove.getEndTime() - duration;
                    double progress = clamp01((clockMs - startTime) / (double) duration);

                    renderRow = r + (dest.getRow() - r) * progress;
                    renderCol = c + (dest.getCol() - c) * progress;
                    state = "move";
                } else if (activeJump != null) {
                    long duration = RealTimeArbiter.JUMP_DURATION_MS;
                    long startTime = activeJump.getEndTime() - duration;
                    double progress = clamp01((clockMs - startTime) / (double) duration);

                    renderRow = r - JUMP_BOUNCE_HEIGHT * Math.sin(Math.PI * progress);
                    state = "jump";
                }

                pieces.add(new PieceSnapshot(p.getType(), p.getColor(), state, renderCol, renderRow));
            }
        }

        return new GameSnapshot(pieces, this.isGameOver, selectedPosition, legalMoves);
    }

    private MoveEvent findActiveMove(Position from) {
        for (GameEvent event : arbiter.getActiveEvents()) {
            if (event instanceof MoveEvent && event.getFromPosition().equals(from)) {
                return (MoveEvent) event;
            }
        }
        return null;
    }

    private JumpEvent findActiveJump(Position from) {
        for (GameEvent event : arbiter.getActiveEvents()) {
            if (event instanceof JumpEvent && event.getFromPosition().equals(from)) {
                return (JumpEvent) event;
            }
        }
        return null;
    }

    private static double clamp01(double v) {
        if (v < 0) return 0;
        if (v > 1) return 1;
        return v;
    }
}