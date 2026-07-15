package view;

public class PieceSnapshot {
    public final char type;
    public final char color;
    public final String state;
    public final double x;
    public final double y;

    public PieceSnapshot(char type, char color, String state, double x, double y) {
        this.type = type;
        this.color = color;
        this.state = state;
        this.x = x;
        this.y = y;
    }
}