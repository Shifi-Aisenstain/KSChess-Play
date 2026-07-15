package controller;

import engine.GameManager;
import models.Piece;
import models.Position;

/**
 * The single owner of the "select a piece, then click a destination" state
 * machine. Both the GUI (via mouse clicks) and the console (via
 * InteractionManager, after it turns pixels into a Position) route through
 * here, so there is exactly one place that knows the selection rules.
 */
public class GameController {
    private final GameManager gameManager;
    private Position selectedPosition = null;

    public GameController(GameManager gameManager) {
        this.gameManager = gameManager;
    }

    public void handleInput(int row, int col) {
        Position targetPos = new Position(row, col);

        if (selectedPosition != null) {
            // Clicked the already-selected square again -> cancel selection.
            if (selectedPosition.equals(targetPos)) {
                selectedPosition = null;
                return;
            }

            Piece selectedPiece = gameManager.getBoard().getPieceAt(selectedPosition);
            Piece targetPiece = gameManager.getBoard().getPieceAt(targetPos);

            // Clicked another piece of the same color -> switch selection to it.
            if (selectedPiece != null && targetPiece != null
                    && selectedPiece.getColor() == targetPiece.getColor()) {
                selectedPosition = targetPos;
                return;
            }

            // Otherwise, attempt the move.
            MoveCommand command = new MoveCommand(selectedPosition, targetPos);
            gameManager.requestMove(command);
            selectedPosition = null;
            return;
        }

        // First click: try to select a piece.
        if (gameManager.getBoard().getPieceAt(targetPos) != null) {
            selectedPosition = targetPos;
        }
    }
    public Position getSelectedPosition() {
        return selectedPosition;
    }
}