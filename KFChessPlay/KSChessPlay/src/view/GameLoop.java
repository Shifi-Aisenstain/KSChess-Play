package view;

import controller.GameController;
import engine.GameManager;
import models.Position;

import javax.swing.*;
import java.awt.image.BufferedImage;
import java.util.Collections;
import java.util.List;

public class GameLoop {
    private final Timer timer;

    public GameLoop(GameManager engine, GameController controller, ImgRenderer renderer,
                    GameWindow window, int fps) {
        int delayMs = 1000 / fps;
        this.timer = new Timer(delayMs, e -> {
            engine.handleWait(delayMs);

            Position selected = controller.getSelectedPosition();
            List<Position> legalMoves = (selected != null)
                    ? engine.getLegalDestinations(selected)
                    : Collections.emptyList();

            GameSnapshot snapshot = engine.createSnapshot(selected, legalMoves);
            BufferedImage frame = renderer.render(snapshot, window.getZoomFactor());

            window.updateCanvas(frame);
            window.updateSidePanel(snapshot);
            window.refresh();
        });
    }

    public void start() { timer.start(); }
    public void stop()  { timer.stop(); }
}
