package shared.protocol.payload;

import lombok.AllArgsConstructor;

@AllArgsConstructor
public final class CommandAckPayload {
    public final boolean accepted;
}
