package view;

import javax.swing.*;
import java.awt.image.BufferedImage;

public class GameLoop {
    private final Timer timer;

    public GameLoop(SnapshotSource snapshotSource, ImgRenderer renderer, GameWindow window, int fps) {
        int delayMs = 1000 / fps;
        this.timer = new Timer(delayMs, e -> {
            GameSnapshot snapshot = snapshotSource.getLatestSnapshot();
            if (snapshot == null) return;

            BufferedImage frame = renderer.render(snapshot, window.getZoomFactor());
            window.updateCanvas(frame);
            window.refresh();
        });
    }

    public void start() { timer.start(); }
    public void stop()  { timer.stop(); }
}
