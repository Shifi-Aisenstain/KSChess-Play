package Test.unit;

import client.audio.SoundSubscriber;
import org.junit.jupiter.api.Test;
import shared.protocol.payload.GameEventPayload;

import static org.junit.jupiter.api.Assertions.*;

public class SoundSubscriberTest {

    private GameEventPayload event(GameEventPayload.Kind kind) {
        return new GameEventPayload(kind, 'w', 'P', '\0', "msg", 0, 0, "");
    }

    @Test
    public void testGameStartedDoesNotThrow() {
        assertDoesNotThrow(() -> new SoundSubscriber().onEvent(event(GameEventPayload.Kind.GAME_STARTED)));
    }

    @Test
    public void testGameEndedDoesNotThrow() {
        assertDoesNotThrow(() -> new SoundSubscriber().onEvent(event(GameEventPayload.Kind.GAME_ENDED)));
    }

    @Test
    public void testPieceCapturedDoesNotThrow() {
        assertDoesNotThrow(() -> new SoundSubscriber().onEvent(event(GameEventPayload.Kind.PIECE_CAPTURED)));
    }

    @Test
    public void testMoveExecutedIsIgnoredWithoutThrowing() {
        assertDoesNotThrow(() -> new SoundSubscriber().onEvent(event(GameEventPayload.Kind.MOVE_EXECUTED)));
    }

    @Test
    public void testPieceJumpedIsIgnoredWithoutThrowing() {
        assertDoesNotThrow(() -> new SoundSubscriber().onEvent(event(GameEventPayload.Kind.PIECE_JUMPED)));
    }
}
