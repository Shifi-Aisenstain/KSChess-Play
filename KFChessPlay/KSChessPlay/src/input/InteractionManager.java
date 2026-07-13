package input;

import controller.MoveCommand;
import models.Position;
import models.Piece;
import engine.GameManager;

/**
 * ✅ InteractionManager: "Dumb Controller" Pattern (CR Requirement Part A)
 * 
 * RESPONSIBILITY: ONLY translate user input to game commands.
 * NO chess logic, NO validation, NO game state management.
 * 
 * THE "DUMB CONTROLLER" PHILOSOPHY:
 * A controller should be as stupid as possible - just a translator.
 * All game logic belongs in GameEngine, RuleEngine, and Arbiter.
 * 
 * WHAT THIS CLASS DOES:
 * 1. handleClick(x, y) - Translates pixel coordinates to Position
 * 2. Tracks selection state (SelectedPosition) - UI state only
 * 3. Creates MoveCommand and delegates to GameManager
 * 
 * WHAT THIS CLASS DOES NOT DO:
 * ❌ Check if move is valid (RuleEngine does that)
 * ❌ Manage game clock or events (Arbiter does that)
 * ❌ Verify player colors (GameManager orchestrates)
 * ❌ Handle piece captures (MoveEvent does that)
 * ❌ Parse pixels to grid (CoordinateParser does that)
 * 
 * INTERACTION FLOW:
 *   1. User clicks (x, y)
 *   2. InteractionManager.handleClick() receives click
 *   3. CoordinateParser.parseClick() translates to Position
 *   4. If Position is null (out of bounds) → return early
 *   5. Track selection state (no logic - just memory)
 *   6. Create MoveCommand and call gameManager.requestMove()
 *   7. GameManager handles ALL game logic
 * 
 * @author Chess Game Architecture
 * @version 1.0 (Dumb Controller Pattern)
 */
public class InteractionManager {
    private final GameManager gameManager;
    private Position selectedPosition = null;

    public InteractionManager(GameManager gameManager) {
        this.gameManager = gameManager;
    }

    public void handleClick(int x, int y) {
        Position clickedPos = CoordinateParser.parseClick(
                x, y,
                gameManager.getBoard().getLength(),
                gameManager.getBoard().getCols()
        );

        if (clickedPos == null) {
            selectedPosition = null;
            return;
        }

        Piece clickedPiece = gameManager.getBoard().getPieceAt(clickedPos);

        // לחיצה ראשונה - בחירת כלי
        if (selectedPosition == null) {
            if (clickedPiece != null) {
                selectedPosition = clickedPos;
            }
            return;
        }

        // 🔥 תיקון טסט 9: אם נבחר כלי, ולחצו על כלי אחר מאותו הצבע -> החלף את הבחירה!
        Piece srcPiece = gameManager.getBoard().getPieceAt(selectedPosition);
        if (clickedPiece != null && srcPiece != null && clickedPiece.getColor() == srcPiece.getColor()) {
            selectedPosition = clickedPos; // החלפת הבחירה לכלי החדש
            return;
        }

        // לחיצה שנייה - ביצוע המהלך
        MoveCommand command = new MoveCommand(selectedPosition, clickedPos);
        gameManager.requestMove(command);
        selectedPosition = null;
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
        selectedPosition = null;
    }
}