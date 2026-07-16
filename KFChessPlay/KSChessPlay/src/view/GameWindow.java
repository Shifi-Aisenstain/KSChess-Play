package view;

import graphics.Image;

import view.GameSnapshot;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.util.List;
import java.util.function.BiConsumer;

public class GameWindow extends JFrame {
    private final JLabel canvasLabel;
    private final JLabel whiteScoreLabel;
    private final JLabel blackScoreLabel;
    private final DefaultTableModel whiteTableModel;
    private final DefaultTableModel blackTableModel;
    private int lastWhiteSize = -1;
    private int lastBlackSize = -1;

    private double zoomFactor = 1.0;
    private static final double ZOOM_STEP = 0.1;
    private static final double ZOOM_MIN  = 0.5;
    private static final double ZOOM_MAX  = 2.0;

    private Runnable restartCallback;

    private static String[] askPlayerNames() {
        JTextField blackField = new JTextField("Black");
        JTextField whiteField = new JTextField("White");
        JPanel panel = new JPanel(new GridLayout(2, 2, 5, 5));
        panel.add(new JLabel("Black player name:"));
        panel.add(blackField);
        panel.add(new JLabel("White player name:"));
        panel.add(whiteField);
        int result = JOptionPane.showConfirmDialog(null, panel, "Enter Player Names",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result == JOptionPane.OK_OPTION) {
            return new String[]{blackField.getText().trim(), whiteField.getText().trim()};
        }
        return new String[]{"Black", "White"};
    }

    public GameWindow(Image canvas) {
        String[] names = askPlayerNames();
        String blackName = names[0].isEmpty() ? "Black" : names[0];
        String whiteName = names[1].isEmpty() ? "White" : names[1];

        setTitle("Kung Fu Chess");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        this.canvasLabel = new JLabel(new ImageIcon(canvas.get()));

        canvasLabel.addMouseWheelListener((MouseWheelEvent e) -> {
            double delta = -e.getPreciseWheelRotation() * ZOOM_STEP;
            zoomFactor = Math.max(ZOOM_MIN, Math.min(ZOOM_MAX, zoomFactor + delta));
            pack();
        });

        JPanel scorePanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 30, 8));
        Font scoreFont = new Font("Arial", Font.BOLD, 18);
        blackScoreLabel = new JLabel(blackName + " (Black)  Score: 0");
        blackScoreLabel.setFont(scoreFont);
        whiteScoreLabel = new JLabel(whiteName + " (White)  Score: 0");
        whiteScoreLabel.setFont(scoreFont);

        JButton restartBtn = new JButton("New Game");
        restartBtn.setFont(new Font("Arial", Font.BOLD, 14));
        restartBtn.addActionListener(e -> {
            if (restartCallback != null) restartCallback.run();
        });

        scorePanel.add(blackScoreLabel);
        scorePanel.add(restartBtn);
        scorePanel.add(whiteScoreLabel);

        blackTableModel = new DefaultTableModel(new String[]{"Time", "Move"}, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };
        whiteTableModel = new DefaultTableModel(new String[]{"Time", "Move"}, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };

        JPanel blackPanel = buildHistoryPanel(blackName + " (Black)", blackTableModel, canvas.getHeight());
        JPanel whitePanel = buildHistoryPanel(whiteName + " (White)", whiteTableModel, canvas.getHeight());

        add(scorePanel, BorderLayout.NORTH);
        add(canvasLabel, BorderLayout.CENTER);
        add(blackPanel, BorderLayout.WEST);
        add(whitePanel, BorderLayout.EAST);

        pack();
    }

    private JPanel buildHistoryPanel(String title, DefaultTableModel model, int height) {
        JTable table = new JTable(model);
        table.setFont(new Font("Monospaced", Font.PLAIN, 12));
        table.getTableHeader().setFont(new Font("Monospaced", Font.BOLD, 12));
        table.setRowHeight(18);
        table.getColumnModel().getColumn(0).setPreferredWidth(70);
        table.getColumnModel().getColumn(1).setPreferredWidth(60);

        JScrollPane scroll = new JScrollPane(table);
        scroll.setPreferredSize(new Dimension(140, height));

        JPanel panel = new JPanel(new BorderLayout());
        JLabel label = new JLabel(title, SwingConstants.CENTER);
        label.setFont(new Font("Arial", Font.BOLD, 13));
        panel.add(label, BorderLayout.NORTH);
        panel.add(scroll, BorderLayout.CENTER);
        panel.setBorder(BorderFactory.createEtchedBorder());
        return panel;
    }

    public void showGameOver(String message) {
        int choice = JOptionPane.showConfirmDialog(this,
                message + "\n\nStart a new game?", "Game Over",
                JOptionPane.YES_NO_OPTION, JOptionPane.INFORMATION_MESSAGE);
        if (choice == JOptionPane.YES_OPTION && restartCallback != null) {
            restartCallback.run();
        }
    }

    public void setRestartCallback(Runnable callback) {
        this.restartCallback = callback;
    }

    public void resetHistoryTables() {
        whiteTableModel.setRowCount(0);
        blackTableModel.setRowCount(0);
        lastWhiteSize = -1;
        lastBlackSize = -1;
    }

    public double getZoomFactor() { return zoomFactor; }

    public void updateCanvas(java.awt.image.BufferedImage frame) {
        canvasLabel.setIcon(new ImageIcon(frame));
        canvasLabel.setPreferredSize(new java.awt.Dimension(frame.getWidth(), frame.getHeight()));
        pack();
    }

    public void refresh() {
        canvasLabel.repaint();
    }

    public void updateSidePanel(GameSnapshot snapshot) {
        String wText = whiteScoreLabel.getText();
        String wName = wText.contains("Score:") ? wText.substring(0, wText.indexOf("Score:")) : wText;
        whiteScoreLabel.setText(wName + "Score: " + snapshot.getScoreWhite());

        String bText = blackScoreLabel.getText();
        String bName = bText.contains("Score:") ? bText.substring(0, bText.indexOf("Score:")) : bText;
        blackScoreLabel.setText(bName + "Score: " + snapshot.getScoreBlack());

        List<String> whiteHistory = snapshot.getWhiteMoveHistory();
        if (whiteHistory.size() != lastWhiteSize) {
            whiteTableModel.setRowCount(0);
            for (String entry : whiteHistory) {
                String[] parts = entry.split("\\|", 2);
                whiteTableModel.addRow(parts.length == 2 ? parts : new String[]{entry, ""});
            }
            lastWhiteSize = whiteHistory.size();
        }

        List<String> blackHistory = snapshot.getBlackMoveHistory();
        if (blackHistory.size() != lastBlackSize) {
            blackTableModel.setRowCount(0);
            for (String entry : blackHistory) {
                String[] parts = entry.split("\\|", 2);
                blackTableModel.addRow(parts.length == 2 ? parts : new String[]{entry, ""});
            }
            lastBlackSize = blackHistory.size();
        }
    }

    public void onClick(BiConsumer<Integer, Integer> handler) {
        canvasLabel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e)) {
                    int x = (int) (e.getX() / zoomFactor);
                    int y = (int) (e.getY() / zoomFactor);
                    handler.accept(x, y);
                }
            }
        });
    }

    public void onRightClick(BiConsumer<Integer, Integer> handler) {
        canvasLabel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    int x = (int) (e.getX() / zoomFactor);
                    int y = (int) (e.getY() / zoomFactor);
                    handler.accept(x, y);
                }
            }
        });
    }
}
