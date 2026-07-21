package test.Test.unit;

import models.Position;
import controller.MoveCommand;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class MoveCommandTest {

    @Test
    public void testMoveCommandGetters() {
        Position src = new Position(0, 0);
        Position dest = new Position(0, 2);
        MoveCommand command = new MoveCommand(src, dest);

        assertEquals(src, command.getSource());
        assertEquals(dest, command.getDestination());
    }
}