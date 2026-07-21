package view;

import models.Position;

public class CooldownHighlight {
    public enum Type { JUMP, LONG_REST, SHORT_REST }

    private final Position position;
    private final double remainingFraction;
    private final Type type;

    public CooldownHighlight(Position position, double remainingFraction, Type type) {
        this.position = position;
        this.remainingFraction = remainingFraction;
        this.type = type;
    }

    public Position getPosition() { return position; }
    public double getRemainingFraction() { return remainingFraction; }
    public Type getType() { return type; }
}
