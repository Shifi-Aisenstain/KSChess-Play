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

    private final JLabel bannerLabel;
    private final JLabel countdownLabel;
    private javax.swing.Timer bannerTimer;

    /** Names are already known from login/matchmaking/room join by the time this is constructed. */
    public GameWindow(Image canvas, String whiteName, String blackName) {
        setTitle("Kung Fu Chess");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        this.canvasLabel = new JLabel(new ImageIcon(canvas.get()));
        // JLabel centers its icon by default. The side history panels keep a fixed
        // preferred height (set once from the unzoomed board), so once zoomFactor
        // shrinks the board image below that height, BorderLayout stretches this
        // label taller than its icon - and centering would draw the image away from
        // (0,0), while onClick/onRightClick below assume raw pixel (0,0) is the
        // image's top-left corner. Pin it there so that assumption always holds.
        canvasLabel.setHorizontalAlignment(SwingConstants.LEFT);
        canvasLabel.setVerticalAlignment(SwingConstants.TOP);

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

        bannerLabel = new JLabel("", SwingConstants.CENTER);
        bannerLabel.setFont(new Font("Arial", Font.BOLD, 16));
        bannerLabel.setOpaque(true);
        bannerLabel.setVisible(false);

        countdownLabel = new JLabel("", SwingConstants.CENTER);
        countdownLabel.setFont(new Font("Arial", Font.BOLD, 14));
        countdownLabel.setForeground(Color.RED);
        countdownLabel.setVisible(false);

        JPanel northStack = new JPanel();
        northStack.setLayout(new BoxLayout(northStack, BoxLayout.Y_AXIS));
        northStack.add(bannerLabel);
        northStack.add(countdownLabel);
        northStack.add(scorePanel);

        add(northStack, BorderLayout.NORTH);
        add(canvasLabel, BorderLayout.CENTER);
        add(blackPanel, BorderLayout.WEST);
        add(whitePanel, BorderLayout.EAST);

        pack();
    }

    /** Networked-play addition: brief on-screen banner for game start/end/capture cues from the server's event bus. */
    public void flashBanner(String text, Color background) {
        bannerLabel.setText(text);
        bannerLabel.setBackground(background);
        bannerLabel.setVisible(true);
        if (bannerTimer != null) bannerTimer.stop();
        bannerTimer = new javax.swing.Timer(2200, e -> bannerLabel.setVisible(false));
        bannerTimer.setRepeats(false);
        bannerTimer.start();
    }

    /** Networked-play addition: spec's "auto-resign after 20 sec, make a count down on the screen". */
    public void showDisconnectCountdown(String playerColorLabel, int secondsRemaining) {
        countdownLabel.setText(playerColorLabel + " disconnected - auto-resign in " + secondsRemaining + "s");
        countdownLabel.setVisible(true);
    }

    public void hideDisconnectCountdown() {
        countdownLabel.setVisible(false);
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

    /**
     * One-time reconciliation for a client that didn't observe every {@code GAME_EVENT} on the bus
     * from the start (e.g. a spectator joining mid-game) - backfills score/history from the first
     * full snapshot received. Ongoing updates during play come from {@link #updateScore} and
     * {@link #appendMoveLogEntry}, driven by the event bus (see {@code client.ui.ScoreboardSubscriber}).
     */
    public void updateSidePanel(GameSnapshot snapshot) {
        updateScore(snapshot.getScoreWhite(), snapshot.getScoreBlack());

        List<String> whiteHistory = snapshot.getWhiteMoveHistory();
        if (whiteHistory.size() != lastWhiteSize) {
            whiteTableModel.setRowCount(0);
            for (String entry : whiteHistory) whiteTableModel.addRow(splitLogEntry(entry));
            lastWhiteSize = whiteHistory.size();
        }

        List<String> blackHistory = snapshot.getBlackMoveHistory();
        if (blackHistory.size() != lastBlackSize) {
            blackTableModel.setRowCount(0);
            for (String entry : blackHistory) blackTableModel.addRow(splitLogEntry(entry));
            lastBlackSize = blackHistory.size();
        }
    }

    /** Bus-driven score update - see {@code client.ui.ScoreboardSubscriber}. */
    public void updateScore(int scoreWhite, int scoreBlack) {
        setScoreLabelText(whiteScoreLabel, scoreWhite);
        setScoreLabelText(blackScoreLabel, scoreBlack);
    }

    /** Bus-driven move-log append - see {@code client.ui.ScoreboardSubscriber}. */
    public void appendMoveLogEntry(char color, String logEntry) {
        if (logEntry == null || logEntry.isEmpty()) return;
        if (color == 'w') {
            whiteTableModel.addRow(splitLogEntry(logEntry));
            lastWhiteSize = whiteTableModel.getRowCount();
        } else {
            blackTableModel.addRow(splitLogEntry(logEntry));
            lastBlackSize = blackTableModel.getRowCount();
        }
    }

    private static String[] splitLogEntry(String entry) {
        String[] parts = entry.split("\\|", 2);
        return parts.length == 2 ? parts : new String[]{entry, ""};
    }

    private static void setScoreLabelText(JLabel label, int score) {
        String text = label.getText();
        String name = text.contains("Score:") ? text.substring(0, text.indexOf("Score:")) : text;
        label.setText(name + "Score: " + score);
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
