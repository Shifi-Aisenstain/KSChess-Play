package input;

import controller.GameController;
import engine.GameManager;
import models.Position;

/**
 * Adapts raw pixel clicks (from the console/script protocol) into board
 * positions and delegates to GameController for the actual selection/move
 * logic, instead of keeping its own duplicate copy of that state machine.
 */
public class InteractionManager {
    private final GameManager gameManager;
    private final GameController controller;

    public InteractionManager(GameManager gameManager) {
        this.gameManager = gameManager;
        this.controller = new GameController(gameManager);
    }

    public void handleClick(int x, int y) {
        Position clickedPos = CoordinateParser.parseClick(
                x, y,
                gameManager.getBoard().getLength(),
                gameManager.getBoard().getCols()
        );
        if (clickedPos == null) {
            return;
        }
        controller.handleInput(clickedPos.getRow(), clickedPos.getCol());
    }

    public void handleJump(int x, int y) {
        Position clickedPos = CoordinateParser.parseClick(
                x, y,
                gameManager.getBoard().getLength(),
                gameManager.getBoard().getCols()
        );
        if (clickedPos != null) {
            gameManager.requestJump(clickedPos);
        }
    }
}