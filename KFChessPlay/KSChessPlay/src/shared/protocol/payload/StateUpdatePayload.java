package shared.protocol.payload;

import lombok.AllArgsConstructor;
import view.GameSnapshot;

/**
 * Wraps the existing (already fully self-contained) {@link GameSnapshot}
 * produced by the reused single-player engine. Nothing about the rendering
 * pipeline had to change client-side - the network layer just becomes the
 * thing that hands GameSnapshot to the renderer instead of GameManager
 * doing it locally.
 */
@AllArgsConstructor
public final class StateUpdatePayload {
    public final String roomId;
    public final char yourColor;   // 'w', 'b', or 's' for spectators
    public final int boardRows;
    public final int boardCols;
    public final GameSnapshot snapshot;
    public final String whiteUsername;
    public final String blackUsername;
}
