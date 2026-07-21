package client.ui;

import javax.swing.*;
import java.awt.*;

/**
 * Spec 1: "Add Home screen with ... support only 2 players" / "Add 'Play'
 * button" / "Add 'Room' button". Purely a view - every button press is
 * forwarded to a {@link HomeScreenListener} (usually
 * {@code client.bridge.NetworkGameController}) which owns what happens on
 * the network; this class never talks to a socket itself.
 */
public final class HomeScreen extends JFrame {
    private final JLabel statusLabel = new JLabel(" ", SwingConstants.CENTER);
    private final JButton playButton = new JButton("Play");
    private final JButton roomButton = new JButton("Room");
    private HomeScreenListener listener;
    private boolean searching = false;

    public HomeScreen(String username, int elo) {
        setTitle("KungFu Chess - Home");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(10, 10));
        setSize(360, 200);
        setLocationRelativeTo(null);

        JLabel welcomeLabel = new JLabel("Welcome, " + username + "  (ELO " + elo + ")", SwingConstants.CENTER);
        welcomeLabel.setFont(new Font("Arial", Font.BOLD, 16));

        JPanel buttonPanel = new JPanel(new FlowLayout());
        playButton.addActionListener(e -> onPlayButtonClicked());
        roomButton.addActionListener(e -> onRoomButtonClicked());
        buttonPanel.add(playButton);
        buttonPanel.add(roomButton);

        add(welcomeLabel, BorderLayout.NORTH);
        add(buttonPanel, BorderLayout.CENTER);
        add(statusLabel, BorderLayout.SOUTH);
    }

    public void setListener(HomeScreenListener listener) {
        this.listener = listener;
    }

    private void onPlayButtonClicked() {
        if (listener == null) return;
        if (!searching) {
            searching = true;
            playButton.setText("Cancel");
            statusLabel.setText("Searching for an opponent (ELO +-100)...");
            listener.onPlayRequested();
        } else {
            searching = false;
            playButton.setText("Play");
            statusLabel.setText(" ");
            listener.onPlayCancelled();
        }
    }

    private void onRoomButtonClicked() {
        if (listener == null) return;
        RoomDialog dialog = new RoomDialog(this);
        dialog.setVisible(true);
        switch (dialog.getOutcome()) {
            case CREATE -> listener.onRoomCreateRequested();
            case JOIN -> {
                String roomId = dialog.getRoomIdText();
                if (!roomId.isEmpty()) listener.onRoomJoinRequested(roomId);
            }
            case CANCEL -> { /* nothing to do */ }
        }
    }

    public void showWaitingForMatch() {
        SwingUtilities.invokeLater(() -> statusLabel.setText("Searching for an opponent (ELO +-100)..."));
    }

    public void showMatchNotFound(String message) {
        SwingUtilities.invokeLater(() -> {
            searching = false;
            playButton.setText("Play");
            statusLabel.setText(" ");
            JOptionPane.showMessageDialog(this, message, "No opponent found", JOptionPane.INFORMATION_MESSAGE);
        });
    }

    public void showRoomError(String message) {
        SwingUtilities.invokeLater(() ->
                JOptionPane.showMessageDialog(this, message, "Room error", JOptionPane.WARNING_MESSAGE));
    }

    public void showRoomCreated(String roomId) {
        SwingUtilities.invokeLater(() ->
                JOptionPane.showMessageDialog(this, "Room created. Share this code: " + roomId,
                        "Room created", JOptionPane.INFORMATION_MESSAGE));
    }
}
