package view;

import lombok.AllArgsConstructor;

@AllArgsConstructor
public class PieceSnapshot {
    public final char type;
    public final char color;
    public final String state;
    public final double x;
    public final double y;
    public final boolean isCaptured;
}
