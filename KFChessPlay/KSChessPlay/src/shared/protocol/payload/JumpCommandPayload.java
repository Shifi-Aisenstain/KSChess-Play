package shared.protocol.payload;

import lombok.AllArgsConstructor;

@AllArgsConstructor
public final class JumpCommandPayload {
    public final int row;
    public final int col;
}
