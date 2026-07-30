package Test.unit;

import loadtest.PlayerRunResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class PlayerRunResultTest {

    @Test
    public void testBuilderCapturesAllSuccessFields() {
        PlayerRunResult result = new PlayerRunResult.Builder(3)
                .login(true, 10)
                .matchmaking(true, 200)
                .moveRoundTrip(true, 50)
                .gameEndedCleanly(true)
                .build();

        assertEquals(3, result.playerIndex);
        assertTrue(result.loginSucceeded);
        assertEquals(10, result.loginLatencyMs);
        assertTrue(result.matchFound);
        assertEquals(200, result.matchmakingLatencyMs);
        assertTrue(result.moveRoundTripCompleted);
        assertEquals(50, result.moveRoundTripMs);
        assertTrue(result.gameEndedCleanly);
        assertNull(result.failureReason);
    }

    @Test
    public void testBuilderCapturesFailureReasonWithoutOtherFieldsSet() {
        PlayerRunResult result = new PlayerRunResult.Builder(7)
                .failureReason("websocket connect failed")
                .build();

        assertEquals(7, result.playerIndex);
        assertFalse(result.loginSucceeded);
        assertFalse(result.matchFound);
        assertFalse(result.moveRoundTripCompleted);
        assertFalse(result.gameEndedCleanly);
        assertEquals("websocket connect failed", result.failureReason);
    }
}
