package view;

import view.*;
import engine.GameManager;
import controller.GameController; // וודאי שזה הייבוא הנכון!
import input.BoardMapper;
import graphics.Image;

import javax.swing.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class GameWindow extends JFrame {
    private ImgRenderer renderer;
    private BoardMapper mapper;
    private GameController controller;
    private GameManager engine;
    private graphics.Image canvas;

    public GameWindow(ImgRenderer renderer, BoardMapper mapper, GameController controller, GameManager engine) {
        this.renderer = renderer;
        this.mapper = mapper;
        this.controller = controller;
        this.engine = engine;

        // אתחול הקנבס
        this.canvas = new graphics.Image();
        this.canvas.read("assets/board_layout.png");

        setTitle("Kung Fu Chess");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        add(new JLabel(new ImageIcon(canvas.get())));
        pack();

        // לופ הרינדור (60 FPS)
        Timer timer = new Timer(16, e -> {
            GameSnapshot snapshot = engine.createSnapshot();
            renderer.render(snapshot, canvas);
            this.repaint();
        });
        timer.start();

        // האזנה לקליקים
        this.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                var position = mapper.pixelToPosition(e.getX(), e.getY());
                controller.handleInput(position.getRow(), position.getCol());
            }
        });
    }
}