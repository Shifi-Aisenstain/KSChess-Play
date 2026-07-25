package shared.protocol.payload;

import lombok.AllArgsConstructor;

/** Structured move request - the server re-derives the actual piece/color from its own board state; this just says which two squares are involved. */
@AllArgsConstructor
public final class MoveCommandPayload {
    public final int fromRow;
    public final int fromCol;
    public final int toRow;
    public final int toCol;
}
