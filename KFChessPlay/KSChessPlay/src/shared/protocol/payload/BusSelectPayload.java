package shared.protocol.payload;

import lombok.AllArgsConstructor;

/** Inner payload of a SELECT {@code RoutedCommandPayload} - same shape as the client-facing
 *  {@link SelectPayload}, plus the userId a bus command needs (see {@link RoutedCommandPayload}). */
@AllArgsConstructor
public final class BusSelectPayload {
    public final long userId;
    public final boolean selected;
    public final int row;
    public final int col;
}
