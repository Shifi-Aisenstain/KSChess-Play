package shared.eventbus.events;

import shared.eventbus.Event;

public final class GameStartedEvent implements Event {
    public final String roomId;
    public final String whiteUsername;
    public final String blackUsername;

    public GameStartedEvent(String roomId, String whiteUsername, String blackUsername) {
        this.roomId = roomId;
        this.whiteUsername = whiteUsername;
        this.blackUsername = blackUsername;
    }
}
