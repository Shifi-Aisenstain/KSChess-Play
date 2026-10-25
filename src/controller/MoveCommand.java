package controller;

import lombok.AllArgsConstructor;
import lombok.Getter;
import models.Position;

@Getter
@AllArgsConstructor
public class MoveCommand {
    private final Position source;
    private final Position destination;
}
