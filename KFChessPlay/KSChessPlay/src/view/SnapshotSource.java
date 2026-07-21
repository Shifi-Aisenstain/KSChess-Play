package view;

/**
 * Whatever feeds {@link GameLoop} a snapshot to paint each frame. The
 * original single-player build pulled straight from {@code engine.GameManager};
 * the networked client instead hands back whichever {@code GameSnapshot} the
 * last {@code STATE_UPDATE} message carried - see
 * {@code client.bridge.NetworkGameController}. Either way GameLoop itself
 * only ever renders, never simulates.
 */
public interface SnapshotSource {
    GameSnapshot getLatestSnapshot();
}
