package client.audio;

import shared.eventbus.Subscriber;
import shared.protocol.payload.GameEventPayload;

import java.awt.Toolkit;

/**
 * "Adding sound" subscriber from the course spec's pub/sub-bus requirement.
 * Wired to the client's local {@link shared.eventbus.EventBus} - see
 * {@code client.bridge.NetworkGameController} - so it never touches the
 * network layer directly. {@link Toolkit#beep()} is a placeholder; swap in
 * {@code javax.sound.sampled.Clip} playback once real WAV assets exist,
 * without changing anything else that publishes to the bus.
 */
public final class SoundSubscriber implements Subscriber<GameEventPayload> {
    @Override
    public void onEvent(GameEventPayload event) {
        switch (event.kind) {
            case GAME_STARTED:
            case GAME_ENDED:
            case PIECE_CAPTURED:
                Toolkit.getDefaultToolkit().beep();
                break;
            default:
                break;
        }
    }
}
