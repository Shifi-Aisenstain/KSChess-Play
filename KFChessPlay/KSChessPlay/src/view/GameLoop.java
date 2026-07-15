package view;

import controller.GameController;
import engine.GameManager;
import graphics.Image;
import models.Position;

import javax.swing.Timer;
import java.util.Collections;
import java.util.List;

/**
 * Owns the periodic render tick: advance the engine's real-time clock,
 * snapshot engine + selection state, draw it onto the shared canvas, ask
 * the window to repaint.
 */
public class GameLoop {
    private final Timer timer;

    public GameLoop(GameManager engine, GameController controller, ImgRenderer renderer,
                    Image canvas, GameWindow window, int fps) {
        int delayMs = 1000 / fps;
        this.timer = new Timer(delayMs, e -> {
            // registerMove()/registerJump() only *schedule* an event on the
            // arbiter's internal game clock - they don't move anything on
            // the board by themselves. Something has to advance that clock
            // every tick so scheduled events actually fire and mutate the
            // board. This is also what createSnapshot() now uses to compute
            // how far along a piece's slide/bounce animation is.
            engine.handleWait(delayMs);

            Position selected = controller.getSelectedPosition();
            List<Position> legalMoves = (selected != null)
                    ? engine.getLegalDestinations(selected)
                    : Collections.emptyList();

            GameSnapshot snapshot = engine.createSnapshot(selected, legalMoves);
            renderer.render(snapshot, canvas);
            window.refresh();
        });
    }

    public void start() {
        timer.start();
    }

    public void stop() {
        timer.stop();
    }
}