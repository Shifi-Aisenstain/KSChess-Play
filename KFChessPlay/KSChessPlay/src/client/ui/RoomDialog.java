package client.ui;

import javax.swing.*;
import java.awt.*;

/**
 * Spec 1.a: "Button: Room - Open a windows message with text box and
 * buttons: Create / Join / Cancel". Create ignores the text box; Join reads
 * the room code from it.
 */
public final class RoomDialog extends JDialog {
    public enum Outcome { CREATE, JOIN, CANCEL }

    private Outcome outcome = Outcome.CANCEL;
    private String roomIdText = "";

    public RoomDialog(Frame owner) {
        super(owner, "Room", true);
        setLayout(new BorderLayout(10, 10));

        JTextField roomIdField = new JTextField(10);
        JPanel inputPanel = new JPanel(new FlowLayout());
        inputPanel.add(new JLabel("Room ID (for Join):"));
        inputPanel.add(roomIdField);

        JButton createBtn = new JButton("Create");
        JButton joinBtn = new JButton("Join");
        JButton cancelBtn = new JButton("Cancel");

        createBtn.addActionListener(e -> { outcome = Outcome.CREATE; dispose(); });
        joinBtn.addActionListener(e -> { outcome = Outcome.JOIN; roomIdText = roomIdField.getText().trim(); dispose(); });
        cancelBtn.addActionListener(e -> { outcome = Outcome.CANCEL; dispose(); });

        JPanel buttonPanel = new JPanel(new FlowLayout());
        buttonPanel.add(createBtn);
        buttonPanel.add(joinBtn);
        buttonPanel.add(cancelBtn);

        add(inputPanel, BorderLayout.CENTER);
        add(buttonPanel, BorderLayout.SOUTH);
        pack();
        setLocationRelativeTo(owner);
    }

    public Outcome getOutcome() { return outcome; }
    public String getRoomIdText() { return roomIdText; }
}
