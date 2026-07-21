package client.ui;

import shared.eventbus.Subscriber;
import shared.protocol.payload.GameEventPayload;
import view.GameWindow;

/** "Update Scores" / "Update Move Logs" subscriber from the pub/sub-bus requirement - the counterpart to {@link client.audio.SoundSubscriber} and {@link AnimationSubscriber} for the other two required bus consumers. */
public final class ScoreboardSubscriber implements Subscriber<GameEventPayload> {
    private final GameWindow window;

    public ScoreboardSubscriber(GameWindow window) {
        this.window = window;
    }

    @Override
    public void onEvent(GameEventPayload event) {
        switch (event.kind) {
            case MOVE_EXECUTED:
            case PIECE_CAPTURED:
                window.updateScore(event.scoreWhite, event.scoreBlack);
                window.appendMoveLogEntry(event.color, event.logEntry);
                break;
            case GAME_STARTED:
                window.resetHistoryTables();
                window.updateScore(0, 0);
                break;
            default:
                break;
        }
    }
}
