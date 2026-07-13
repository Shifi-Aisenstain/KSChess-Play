package models;

public class Piece {
    private final char color; // 'w' או 'b'
    private final char type;  // 'R', 'K', 'P', 'Q'

    public Piece(char color, char type) {
        this.color = color;
        this.type = type;
    }

    public char getColor() { return color; }
    public char getType() { return type; }
}