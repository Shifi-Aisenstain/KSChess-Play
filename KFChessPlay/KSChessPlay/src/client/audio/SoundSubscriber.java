package client.audio;

import shared.eventbus.Subscriber;
import shared.protocol.payload.GameEventPayload;

import java.awt.Toolkit;

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
