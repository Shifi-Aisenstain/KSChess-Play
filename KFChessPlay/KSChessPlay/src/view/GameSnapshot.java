package view;
import java.util.List;

// מחזיק את כל מה שצריך כדי לצייר מסך בודד
public class GameSnapshot {
    private final List<PieceSnapshot> pieces;
    private final boolean isGameOver;
    // אפשר להוסיף כאן גם נתוני סקור אם צריך

    public GameSnapshot(List<PieceSnapshot> pieces, boolean isGameOver) {
        this.pieces = pieces;
        this.isGameOver = isGameOver;
    }

    public List<PieceSnapshot> getPieces() { return pieces; }
    public boolean isGameOver() { return isGameOver; }

}

