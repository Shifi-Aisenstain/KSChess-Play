package shared.protocol.payload;

import lombok.AllArgsConstructor;

/** Wire format for the move command mandated by the spec, e.g. {@code "WQe2e5"}. */
@AllArgsConstructor
public final class MoveCommandPayload {
    public final String command;
}
