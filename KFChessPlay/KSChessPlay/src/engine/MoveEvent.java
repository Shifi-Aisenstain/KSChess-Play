package engine;

import models.Board;
import models.Piece;
import models.Position;
import java.util.List;

public class MoveEvent extends GameEvent {
    private final Position toPosition;

    public MoveEvent(Piece piece, Position fromPosition, Position toPosition, long endTime) {
        super(piece, fromPosition, endTime);
        this.toPosition = toPosition;
    }

    public Position getToPosition() { return toPosition; }

    @Override
    public int getPriority() { return 1; }

    @Override
    public void execute(Board board, List<GameEvent> activeEvents, GameManager gameManager) {
        boolean capturedByJumper = false;

        for (GameEvent event : activeEvents) {
            if (event instanceof JumpEvent
                    && event.getFromPosition().equals(this.toPosition)
                    && event.getPiece().getColor() != this.piece.getColor()) {
                capturedByJumper = true;
                break;
            }
        }

        if (capturedByJumper) {
            gameManager.clearPosition(this.fromPosition);
            return;
        }

        Position blocker = findFirstBlockingSquare(board);
        Position effectiveDestination = this.toPosition;

        if (blocker != null) {
            Piece occupant = board.getPieceAt(blocker);
            if (occupant.getColor() == this.piece.getColor()) {
                return;
            }
            effectiveDestination = blocker;
        }

        Piece pieceToPlace = this.piece;
        if (this.piece.getType() == 'P') {
            int promotionRow = (this.piece.getColor() == 'w') ? 0 : board.getLength() - 1;
            if (effectiveDestination.getRow() == promotionRow) {
                pieceToPlace = new Piece(this.piece.getColor(), 'Q');
            }
        }
        gameManager.executeActualMove(this.fromPosition, effectiveDestination, pieceToPlace);
        gameManager.registerLongRestCooldown(pieceToPlace, effectiveDestination);
    }

    private Position findFirstBlockingSquare(Board board) {
        char type = this.piece.getType();
        boolean isRayPiece = (type == 'R' || type == 'B' || type == 'Q');

        if (!isRayPiece) {
            return (board.getPieceAt(this.toPosition) != null) ? this.toPosition : null;
        }

        int dr = Integer.signum(toPosition.getRow() - fromPosition.getRow());
        int dc = Integer.signum(toPosition.getCol() - fromPosition.getCol());
        int r = fromPosition.getRow() + dr;
        int c = fromPosition.getCol() + dc;

        while (true) {
            Position current = new Position(r, c);
            if (board.getPieceAt(current) != null) {
                return current;
            }
            if (current.equals(toPosition)) {
                return null;
            }
            r += dr;
            c += dc;
        }
    }
}
