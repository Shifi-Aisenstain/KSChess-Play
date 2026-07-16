package controller;

import engine.GameManager;
import models.Piece;
import models.Position;

public class GameController {
    private final GameManager gameManager;
    private Position selectedPosition = null;

    public GameController(GameManager gameManager) {
        this.gameManager = gameManager;
    }

    public void handleInput(int row, int col) {
        Position targetPos = new Position(row, col);

        if (selectedPosition != null) {
            if (selectedPosition.equals(targetPos)) {
                selectedPosition = null;
                return;
            }

            if (gameManager.sameColorAt(selectedPosition, targetPos)) {
                selectedPosition = targetPos;
                return;
            }

            gameManager.requestMove(new MoveCommand(selectedPosition, targetPos));
            selectedPosition = null;
            return;
        }

        if (gameManager.hasPieceAt(targetPos)) {
            selectedPosition = targetPos;
        }
    }

    public void requestJump(int row, int col) {
        gameManager.requestJump(new Position(row, col));
    }

    public Position getSelectedPosition() { return selectedPosition; }

    public void reset() { selectedPosition = null; }
}
