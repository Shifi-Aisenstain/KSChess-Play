package engine;

import controller.MoveCommand;
import models.Board;
import models.Piece;
import models.Position;
import rules.RuleEngine;
import realtime.RealTimeArbiter;
import shared.eventbus.Event;
import shared.eventbus.EventBus;
import shared.eventbus.events.MoveExecutedEvent;
import shared.eventbus.events.PieceJumpedEvent;
import view.CooldownHighlight;
import view.GameSnapshot;
import view.PieceSnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Core real-time chess engine. Unchanged in behaviour from the single-player
 * version - the only addition is an {@link EventBus} hook so that server-side
 * code (scoreboards, move logs, sound cues, animations - see the course spec's
 * "pub/sub bus" requirement) can react to what happens on the board without
 * GameManager needing to know who's listening.
 *
 * <p>The no-arg constructor is preserved (and used by the existing unit/
 * integration tests) - it just wires up a private, unshared bus so nothing
 * publishes anywhere visible. Server code uses {@link #GameManager(String, EventBus)}
 * so the events end up on the shared per-room bus instead.
 */
public class GameManager {
    private static final double JUMP_BOUNCE_HEIGHT = 0.3;

    private Board board;
    private final RuleEngine ruleEngine = new RuleEngine();
    private final RealTimeArbiter arbiter = new RealTimeArbiter();
    private final MoveLogger moveLogger = new MoveLogger();
    private final EventBus eventBus;
    private final String roomId;
    private boolean isGameOver = false;

    public GameManager() {
        this(null, new EventBus());
    }

    /**
     * @param roomId   identifies which game session published events belong to
     *                 (a single server process can host many concurrent rooms
     *                 sharing handler/logger classes, so events are tagged).
     * @param eventBus the bus events are published on; pass the same instance
     *                 used elsewhere in the room/session so subscribers see them.
     */
    public GameManager(String roomId, EventBus eventBus) {
        this.roomId = roomId;
        this.eventBus = eventBus;
    }

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
        if (arbiter.isPieceBusy(command.getSource().getRow(), command.getSource().getCol())) return;
        Piece piece = board.getPieceAt(command.getSource());
        arbiter.registerMove(piece, command.getSource(), command.getDestination());
    }

    public void requestJump(Position pos) {
        if (isGameOver || board == null) return;
        var validation = ruleEngine.validateJump(board, pos);
        if (!validation.isValid()) return;
        if (arbiter.isPieceBusy(pos.getRow(), pos.getCol())) return;
        Piece piece = board.getPieceAt(pos);
        arbiter.registerJump(piece, pos);
        publish(new PieceJumpedEvent(roomId, piece, pos));
    }

    public void handleWait(int ms) {
        arbiter.advanceTime(ms, this);
    }

    public void executeActualMove(Position src, Position dest, Piece pieceToPlace) {
        if (board == null) return;
        Piece target = board.getPieceAt(dest);
        if (target != null && target.getType() == 'K') {
            this.isGameOver = true;
        }
        moveLogger.recordMove(src, dest, pieceToPlace, target, board);
        board.setPieceAt(dest, pieceToPlace);
        board.setPieceAt(src, null);

        String notation = lastNotation(pieceToPlace.getColor());
        String logEntry = moveLogger.getLastEntry(pieceToPlace.getColor());
        publish(new MoveExecutedEvent(roomId, pieceToPlace, src, dest, target, notation,
                moveLogger.getScoreWhite(), moveLogger.getScoreBlack(), logEntry));
    }

    public void registerLongRestCooldown(Piece piece, Position pos) {
        arbiter.registerLongRestCooldown(piece, pos);
    }

    public void clearPosition(Position pos) {
        if (board != null) board.setPieceAt(pos, null);
    }

    public boolean hasPieceAt(Position pos) {
        return board != null && board.getPieceAt(pos) != null;
    }

    public boolean sameColorAt(Position a, Position b) {
        if (board == null) return false;
        Piece pa = board.getPieceAt(a);
        Piece pb = board.getPieceAt(b);
        return pa != null && pb != null && pa.getColor() == pb.getColor();
    }

    public boolean isPieceBusy(int row, int col) {
        return arbiter.isPieceBusy(row, col);
    }

    public void reset() {
        this.board = null;
        this.isGameOver = false;
        this.arbiter.reset();
        this.moveLogger.reset();
    }

    public void setGameOver(boolean gameOver) { this.isGameOver = gameOver; }
    public Board getBoard() { return this.board; }
    public boolean isGameOver() { return this.isGameOver; }
    public String[][] getUpdatedBoardMatrix() { return board.getReadOnlyMatrixView(); }

    /** Which color owns the piece currently sitting on {@code pos}, or {@code '\0'} if empty. Used server-side for ownership checks. */
    public char colorAt(Position pos) {
        if (board == null) return '\0';
        Piece p = board.getPieceAt(pos);
        return p == null ? '\0' : p.getColor();
    }

    public List<Position> getLegalDestinations(Position src) {
        List<Position> result = new ArrayList<>();
        if (board == null || src == null) return result;
        for (int r = 0; r < board.getLength(); r++) {
            for (int c = 0; c < board.getCols(); c++) {
                Position dest = new Position(r, c);
                if (ruleEngine.validateMove(board, src, dest).isValid()) result.add(dest);
            }
        }
        return result;
    }

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
                double renderRow = r, renderCol = c;
                String state = "idle";

                if (activeMove != null) {
                    Position dest = activeMove.getToPosition();
                    int distance = Math.max(Math.abs(dest.getRow() - r), Math.abs(dest.getCol() - c));
                    long duration = Math.max(1L, distance * RealTimeArbiter.TIME_PER_CELL_MS);
                    double progress = clamp01((clockMs - (activeMove.getEndTime() - duration)) / (double) duration);
                    renderRow = r + (dest.getRow() - r) * progress;
                    renderCol = c + (dest.getCol() - c) * progress;
                    state = "move";
                } else if (activeJump != null) {
                    long duration = RealTimeArbiter.JUMP_DURATION_MS;
                    double progress = clamp01((clockMs - (activeJump.getEndTime() - duration)) / (double) duration);
                    renderRow = r - JUMP_BOUNCE_HEIGHT * Math.sin(Math.PI * progress);
                    state = "jump";
                }

                pieces.add(new PieceSnapshot(p.getType(), p.getColor(), state, renderCol, renderRow, false));
            }
        }

        List<CooldownHighlight> cooldownHighlights = new ArrayList<>();
        for (GameEvent event : arbiter.getActiveEvents()) {
            if (event instanceof JumpEvent) {
                long remainingMs = event.getEndTime() - clockMs;
                double fraction = clamp01(remainingMs / (double) RealTimeArbiter.JUMP_DURATION_MS);
                cooldownHighlights.add(new CooldownHighlight(event.getFromPosition(), fraction, CooldownHighlight.Type.JUMP));
            } else if (event instanceof CooldownEvent) {
                CooldownEvent ce = (CooldownEvent) event;
                long duration = (ce.getCooldownType() == CooldownEvent.Type.LONG_REST)
                        ? RealTimeArbiter.LONG_REST_MS
                        : RealTimeArbiter.JUMP_DURATION_MS + RealTimeArbiter.SHORT_REST_MS;
                double fraction = clamp01((ce.getEndTime() - clockMs) / (double) duration);
                CooldownHighlight.Type hlType = (ce.getCooldownType() == CooldownEvent.Type.LONG_REST)
                        ? CooldownHighlight.Type.LONG_REST : CooldownHighlight.Type.SHORT_REST;
                cooldownHighlights.add(new CooldownHighlight(ce.getFromPosition(), fraction, hlType));
            }
        }

        String winner = isGameOver ? resolveWinner() : null;

        return new GameSnapshot(
                pieces, isGameOver, winner, selectedPosition, legalMoves, cooldownHighlights,
                moveLogger.getScoreWhite(), moveLogger.getScoreBlack(),
                moveLogger.getWhiteMoveHistory(), moveLogger.getBlackMoveHistory()
        );
    }

    public GameSnapshot createSnapshot() {
        return createSnapshot(null, Collections.emptyList());
    }

    private String resolveWinner() {
        boolean whiteKingAlive = false, blackKingAlive = false;
        for (int r = 0; r < board.getLength(); r++) {
            for (int c = 0; c < board.getCols(); c++) {
                Piece p = board.getPieceAt(new Position(r, c));
                if (p != null && p.getType() == 'K') {
                    if (p.getColor() == 'w') whiteKingAlive = true;
                    else blackKingAlive = true;
                }
            }
        }
        if (whiteKingAlive && !blackKingAlive) return "White";
        if (blackKingAlive && !whiteKingAlive) return "Black";
        return null;
    }

    private MoveEvent findActiveMove(Position from) {
        for (GameEvent event : arbiter.getActiveEvents()) {
            if (event instanceof MoveEvent && event.getFromPosition().equals(from)) return (MoveEvent) event;
        }
        return null;
    }

    private JumpEvent findActiveJump(Position from) {
        for (GameEvent event : arbiter.getActiveEvents()) {
            if (event instanceof JumpEvent && event.getFromPosition().equals(from)) return (JumpEvent) event;
        }
        return null;
    }

    private String lastNotation(char color) {
        List<String> history = (color == 'w') ? moveLogger.getWhiteMoveHistory() : moveLogger.getBlackMoveHistory();
        if (history.isEmpty()) return "";
        String entry = history.get(history.size() - 1);
        int sep = entry.indexOf('|');
        return sep >= 0 ? entry.substring(sep + 1) : entry;
    }

    private <T extends Event> void publish(T event) {
        if (eventBus != null) eventBus.publish(event);
    }

    private static double clamp01(double v) {
        return v < 0 ? 0 : v > 1 ? 1 : v;
    }
}
