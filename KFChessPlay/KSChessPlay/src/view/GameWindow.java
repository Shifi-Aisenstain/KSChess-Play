package view;
import javax.swing.*;

import graphics.Image;
import input.BoardMapper;
import controller.  GameController; // ה-Controller שלך

public class GameWindow extends JFrame {
    private ImgRenderer renderer;
    private BoardMapper mapper;
    private GameController controller;
    private graphics.Image canvas; // ה-Canvas שעליו את מציירת

    public GameWindow(ImgRenderer renderer, BoardMapper mapper, GameController controller) {
        this.renderer = renderer;
        this.mapper = mapper;
        this.controller = controller;
        this.canvas = new Image();            // 1. יצירת אובייקט ריק
        this.canvas.read("assets/board_layout.png");/ דוגמה ל-Canvas התחלתי

        // לופ המשחק: ריענון כל 16ms (כ-60 FPS)
        Timer timer = new Timer(16, e -> {
            // כאן תשלפי את ה-Snapshot העדכני מה-Engine
            GameSnapshot snapshot = engine.createSnapshot();
            renderer.render(snapshot, canvas);
            this.repaint(); // מרענן את החלון
        });
        timer.start();

        // האזנה לקליקים
        this.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                // המרה למיקום לוגי ושליחה ל-Controller
                var position = mapper.pixelToPosition(e.getX(), e.getY());
                controller.handleInput(position.getRow(), position.getCol());
            }
        });
    }
}