package realtime;

import models.Piece;
import models.Position;

public class Motion {
    private Piece piece;
    private Position from;
    private Position to;
    private float startTime;
    private float duration;

    public Motion(Piece piece, Position from, Position to, float startTime) {
        this.piece = piece;
        this.from = from;
        this.to = to;
        this.startTime = startTime;
        this.duration = calculateDuration(from, to);
    }

    /**
     * חישוב משך התנועה האמיתי: 1.0 שנייה (1000ms) לכל משבצת, לפי הטסטים!
     */
    private float calculateDuration(Position from, Position to) {
        int rowDiff = Math.abs(to.getRow() - from.getRow());
        int colDiff = Math.abs(to.getCol() - from.getCol());
        int distance = Math.max(rowDiff, colDiff); // תנועה מקבילית או אלכסונית
        return distance * 1.0f; // שנייה אחת למשבצת
    }

    public Piece getPiece() { return piece; }
    public Position getFrom() { return from; }
    public Position getTo() { return to; }
    public float getStartTime() { return startTime; }
    public int getRow() { return from.getRow(); }
    public int getCol() { return from.getCol(); }
    public Position getDestination() { return to; }

    public boolean isFinished(float currentTime) {
        return currentTime >= (startTime + duration);
    }
}