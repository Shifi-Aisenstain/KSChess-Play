package view;
public class PieceSnapshot {
    public char type, color;
    public String state;
    public double x, y; // מטרים
    public PieceSnapshot(char t, char c, String s, double x, double y) {
        this.type = t; this.color = c; this.state = s; this.x = x; this.y = y;
    }
}