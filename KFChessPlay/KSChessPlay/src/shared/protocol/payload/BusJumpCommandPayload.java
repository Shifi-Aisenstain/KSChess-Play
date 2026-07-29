package shared.protocol.payload;

import lombok.AllArgsConstructor;

/** Inner payload of a JUMP {@code RoutedCommandPayload} - same shape as the client-facing
 *  {@link JumpCommandPayload}, plus the userId a bus command needs (see {@link RoutedCommandPayload}). */
@AllArgsConstructor
public final class BusJumpCommandPayload {
    public final long userId;
    public final int row;
    public final int col;
}
