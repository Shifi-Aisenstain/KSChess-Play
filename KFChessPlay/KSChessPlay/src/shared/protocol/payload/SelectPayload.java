package shared.protocol.payload;

/** Client -> server "I clicked this square" notice. {@code selected=false} means deselect. */
public final class SelectPayload {
    public final boolean selected;
    public final int row;
    public final int col;

    public SelectPayload(boolean selected, int row, int col) {
        this.selected = selected;
        this.row = row;
        this.col = col;
    }
}
