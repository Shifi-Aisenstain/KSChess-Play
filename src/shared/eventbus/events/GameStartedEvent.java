package shared.eventbus.events;

import lombok.AllArgsConstructor;
import shared.eventbus.Event;

@AllArgsConstructor
public final class GameStartedEvent implements Event {
    public final String roomId;
    public final String whiteUsername;
    public final String blackUsername;
}
