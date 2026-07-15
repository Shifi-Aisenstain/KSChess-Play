package view;

import graphics.Image;

import javax.swing.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.BiConsumer;

/**
 * Pure view: displays whatever is currently drawn on the shared canvas and
 * forwards raw mouse clicks to whoever registers interest via onClick().
 * It does NOT load assets, run the render loop, or interpret pixel
 * coordinates - those jobs belong to Main, GameLoop, and CoordinateParser
 * respectively. (Previously this one class did all four things.)
 */
public class GameWindow extends JFrame {
    private final JLabel canvasLabel;

    public GameWindow(Image canvas) {
        setTitle("Kung Fu Chess");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        this.canvasLabel = new JLabel(new ImageIcon(canvas.get()));
        add(canvasLabel);
        pack();
    }

    /** Call after the canvas image has been redrawn in place, to repaint the window. */
    public void refresh() {
        canvasLabel.repaint();
    }

    /** Registers a callback that receives raw pixel coordinates on each click. */
    public void onClick(BiConsumer<Integer, Integer> handler) {
        // Listener must be attached to canvasLabel, not the JFrame itself.
        // canvasLabel fully covers the frame's content pane, so it is the
        // component that actually receives the mouse events; a listener on
        // the frame never fires for clicks that land on a child component.
        // This is why clicks previously did nothing at all.
        canvasLabel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                handler.accept(e.getX(), e.getY());
            }
        });
    }
}