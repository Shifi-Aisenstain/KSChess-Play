package shared.eventbus.events;

import lombok.AllArgsConstructor;
import shared.eventbus.Event;

@AllArgsConstructor
public final class GameEndedEvent implements Event {
    public final String roomId;
    public final String winnerColor;
    public final String reason;
    public final String whiteUsername;
    public final String blackUsername;
}
