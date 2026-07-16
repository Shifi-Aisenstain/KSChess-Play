package input;

import controller.GameController;
import engine.GameManager;
import models.Position;

public class InteractionManager {
    private final GameManager gameManager;
    private final GameController controller;

    public InteractionManager(GameManager gameManager, GameController controller) {
        this.gameManager = gameManager;
        this.controller = controller;
    }

    public void handleClick(int x, int y) {
        Position clickedPos = CoordinateParser.parseClick(
                x, y,
                gameManager.getBoard().getLength(),
                gameManager.getBoard().getCols()
        );
        if (clickedPos == null) return;
        controller.handleInput(clickedPos.getRow(), clickedPos.getCol());
    }

    public void handleJump(int x, int y) {
        Position clickedPos = CoordinateParser.parseClick(
                x, y,
                gameManager.getBoard().getLength(),
                gameManager.getBoard().getCols()
        );
        if (clickedPos != null) {
            controller.requestJump(clickedPos.getRow(), clickedPos.getCol());
        }
    }
}
