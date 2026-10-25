package view;

import lombok.AllArgsConstructor;
import lombok.Getter;
import models.Position;

@Getter
@AllArgsConstructor
public class CooldownHighlight {
    public enum Type { JUMP, LONG_REST, SHORT_REST }

    private final Position position;
    private final double remainingFraction;
    private final Type type;
}
