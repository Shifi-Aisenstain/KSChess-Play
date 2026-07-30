package shared.protocol.payload;

import lombok.AllArgsConstructor;

@AllArgsConstructor
public final class SelectPayload {
    public final boolean selected;
    public final int row;
    public final int col;
}
