package shared.protocol.payload;

import lombok.AllArgsConstructor;

@AllArgsConstructor
public final class MoveCommandPayload {
    public final int fromRow;
    public final int fromCol;
    public final int toRow;
    public final int toCol;
}
