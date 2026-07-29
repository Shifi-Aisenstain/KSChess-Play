package shared.protocol.payload;

import lombok.AllArgsConstructor;

/** Inner payload of a MOVE {@code RoutedCommandPayload} - same shape as the client-facing
 *  {@link MoveCommandPayload}, plus the userId a bus command needs (see {@link RoutedCommandPayload}). */
@AllArgsConstructor
public final class BusMoveCommandPayload {
    public final long userId;
    public final int fromRow;
    public final int fromCol;
    public final int toRow;
    public final int toCol;
}
