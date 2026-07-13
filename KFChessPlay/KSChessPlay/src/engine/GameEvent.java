package engine;

import models.Board;
import models.Piece;
import models.Position;
import java.util.List;

public abstract class GameEvent {
    protected final Piece piece;
    protected final Position fromPosition;
    protected final long endTime;

    public GameEvent(Piece piece, Position fromPosition, long endTime) {
        this.piece = piece;
        this.fromPosition = fromPosition;
        this.endTime = endTime;
    }

    public Piece getPiece() { return piece; }
    public Position getFromPosition() { return fromPosition; }
    public long getEndTime() { return endTime; }

    // 🔥 מתודה פולימורפית חדשה המגדירה את סדר העדיפות של האירוע
    public abstract int getPriority();

    public abstract void execute(Board board, List<GameEvent> activeEvents, GameManager gameManager);
}