package shared.eventbus.events;

import shared.eventbus.Event;

public final class GameEndedEvent implements Event {
    public final String roomId;
    public final String winnerColor; // "WHITE" | "BLACK" | null
    public final String reason;
    public final String whiteUsername;
    public final String blackUsername;

    public GameEndedEvent(String roomId, String winnerColor, String reason, String whiteUsername, String blackUsername) {
        this.roomId = roomId;
        this.winnerColor = winnerColor;
        this.reason = reason;
        this.whiteUsername = whiteUsername;
        this.blackUsername = blackUsername;
    }
}
