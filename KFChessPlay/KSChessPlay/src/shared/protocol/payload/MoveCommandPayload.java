package shared.protocol.payload;

/** Wire format for the move command mandated by the spec, e.g. {@code "WQe2e5"}. */
public final class MoveCommandPayload {
    public final String command;

    public MoveCommandPayload(String command) {
        this.command = command;
    }
}
