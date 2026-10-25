package shared.protocol.payload;

import lombok.AllArgsConstructor;

@AllArgsConstructor
public final class BusSelectPayload {
    public final long userId;
    public final boolean selected;
    public final int row;
    public final int col;
}
