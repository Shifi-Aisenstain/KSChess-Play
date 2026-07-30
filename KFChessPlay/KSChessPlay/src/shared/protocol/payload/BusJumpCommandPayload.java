package shared.protocol.payload;

import lombok.AllArgsConstructor;

@AllArgsConstructor
public final class BusJumpCommandPayload {
    public final long userId;
    public final int row;
    public final int col;
}
