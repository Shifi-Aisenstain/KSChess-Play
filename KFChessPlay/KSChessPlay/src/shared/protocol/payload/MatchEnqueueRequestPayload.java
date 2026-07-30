package shared.protocol.payload;

import lombok.AllArgsConstructor;

@AllArgsConstructor
public final class MatchEnqueueRequestPayload {
    public final long userId;
    public final String username;
    public final int elo;
}
