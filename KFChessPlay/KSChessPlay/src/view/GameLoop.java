package view;

import javax.swing.*;
import java.awt.image.BufferedImage;

/**
 * Networked-play version: no longer owns simulation time. Each tick just
 * asks {@link SnapshotSource} for whatever the server last pushed and
 * repaints the board - all the real-time cooldown/move math now happens
 * server-side inside {@code server.game.GameSession}, once per room instead
 * of once per client. Score and move-log updates are NOT driven from here:
 * per the bus requirement, those flow from discrete {@code GAME_EVENT}s via
 * {@code client.ui.ScoreboardSubscriber} instead of being re-derived from
 * every polled snapshot.
 */
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
