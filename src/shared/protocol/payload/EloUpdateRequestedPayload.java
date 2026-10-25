package shared.protocol.payload;

import lombok.AllArgsConstructor;

@AllArgsConstructor
public final class EloUpdateRequestedPayload {
    public final long userId;
    public final int newElo;
}
