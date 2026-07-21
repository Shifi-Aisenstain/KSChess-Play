package test.Test.unit;

import input.InteractionManager;
import engine.GameManager;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class InteractionManagerTest {

    @Test
    public void testInteractionManagerInitialization() {
        GameManager gameManager = new GameManager();
        InteractionManager interactionManager = new InteractionManager(gameManager);
        assertNotNull(interactionManager);
    }
}