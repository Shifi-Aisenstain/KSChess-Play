package shared.protocol.payload;

public final class JumpCommandPayload {
    public final int row;
    public final int col;

    public JumpCommandPayload(int row, int col) {
        this.row = row;
        this.col = col;
    }
}
