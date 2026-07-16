package view;

import models.Position;

public class JumpHighlight {
    private final Position position;
    private final double remainingFraction;

    public JumpHighlight(Position position, double remainingFraction) {
        this.position = position;
        this.remainingFraction = remainingFraction;
    }

    public Position getPosition() { return position; }
    public double getRemainingFraction() { return remainingFraction; }
}