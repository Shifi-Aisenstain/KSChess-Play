package shared.protocol.payload;

import lombok.AllArgsConstructor;
import view.GameSnapshot;

@AllArgsConstructor
public final class StateUpdatePayload {
    public final String roomId;
    public final char yourColor;
    public final int boardRows;
    public final int boardCols;
    public final GameSnapshot snapshot;
    public final String whiteUsername;
    public final String blackUsername;
}
