package shared.protocol.payload;

import lombok.AllArgsConstructor;

/** Client -> server "I clicked this square" notice. {@code selected=false} means deselect. */
@AllArgsConstructor
public final class SelectPayload {
    public final boolean selected;
    public final int row;
    public final int col;
}
