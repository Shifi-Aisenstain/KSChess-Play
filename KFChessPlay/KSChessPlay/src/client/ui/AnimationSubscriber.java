package client.ui;

import shared.eventbus.Subscriber;
import shared.protocol.payload.GameEventPayload;
import view.GameWindow;

import java.awt.Color;

/** "Game start/end animations" subscriber from the pub/sub-bus requirement. */
public final class AnimationSubscriber implements Subscriber<GameEventPayload> {
    private final GameWindow window;

    public AnimationSubscriber(GameWindow window) {
        this.window = window;
    }

    @Override
    public void onEvent(GameEventPayload event) {
        switch (event.kind) {
            case GAME_STARTED:
                window.flashBanner("Game started!", new Color(60, 179, 113));
                break;
            case GAME_ENDED:
                window.flashBanner(event.message, new Color(220, 20, 60));
                break;
            case PIECE_CAPTURED:
                window.flashBanner(event.message, new Color(255, 165, 0));
                break;
            default:
                break;
        }
    }
}
