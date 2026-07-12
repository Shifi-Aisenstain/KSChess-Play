package Engine;

import Models.Board;
import java.util.List;

public abstract class GameEvent {
    protected final Models.Piece piece;
    protected final int fromRow;
    protected final int fromCol;
    protected final long endTime;

    public GameEvent(Models.Piece piece, int fromRow, int fromCol, long endTime) {
        this.piece = piece;
        this.fromRow = fromRow;
        this.fromCol = fromCol;
        this.endTime = endTime;
    }

    public Models.Piece getPiece() { return piece; }
    public int getFromRow() { return fromRow; }
    public int getFromCol() { return fromCol; }
    public long getEndTime() { return endTime; }

    // 🔥 כל אירוע יבצע את עצמו וישפיע על הלוח ועל רשימת האירועים
    public abstract void execute(Board board, List<GameEvent> activeEvents, GameManager gameManager);
}