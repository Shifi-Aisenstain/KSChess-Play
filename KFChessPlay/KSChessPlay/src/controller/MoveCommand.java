package controller;

import models.Position;

// אובייקט פקודה פשוט המייצג את כוונת המשתמש
public class MoveCommand {
    private final Position source;
    private final Position destination;

    public MoveCommand(Position source, Position destination) {
        this.source = source;
        this.destination = destination;
    }

    public Position getSource() { return source; }
    public Position getDestination() { return destination; }
}