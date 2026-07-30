package shared.protocol.payload;

import lombok.AllArgsConstructor;

@AllArgsConstructor
public final class BusResignPayload {
    public final long userId;
    public final boolean dueToDisconnect;
}
