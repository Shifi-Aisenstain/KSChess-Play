package Test.unit;

import org.junit.jupiter.api.Test;
import server.rating.EloRatingService;
import server.rating.RatingService;

import static org.junit.jupiter.api.Assertions.*;

public class EloRatingServiceTest {

    private final EloRatingService service = new EloRatingService();

    @Test
    public void testEqualEloWinnerGainsHalfKFactor() {
        RatingService.EloUpdate update = service.computeUpdate(1200, 1200, "WHITE");

        assertEquals(16, update.whiteDelta);
        assertEquals(-16, update.blackDelta);
        assertEquals(1216, update.whiteNewElo);
        assertEquals(1184, update.blackNewElo);
    }

    @Test
    public void testEqualEloLoserLosesHalfKFactor() {
        RatingService.EloUpdate update = service.computeUpdate(1200, 1200, "BLACK");

        assertEquals(-16, update.whiteDelta);
        assertEquals(16, update.blackDelta);
    }

    @Test
    public void testDrawBetweenEqualRatingsIsANoOp() {
        RatingService.EloUpdate update = service.computeUpdate(1200, 1200, null);

        assertEquals(0, update.whiteDelta);
        assertEquals(0, update.blackDelta);
    }

    @Test
    public void testUnderdogWinnerGainsMoreThanKFactorHalf() {
        // White is rated much lower and still wins - should gain close to the full K-factor.
        RatingService.EloUpdate update = service.computeUpdate(1000, 1400, "WHITE");

        assertTrue(update.whiteDelta > 16);
        assertTrue(update.whiteDelta <= 32);
    }

    @Test
    public void testFavoriteWinningGainsLessThanKFactorHalf() {
        RatingService.EloUpdate update = service.computeUpdate(1400, 1000, "WHITE");

        assertTrue(update.whiteDelta < 16);
        assertTrue(update.whiteDelta >= 0);
    }

    @Test
    public void testWinnerColorIsCaseInsensitive() {
        RatingService.EloUpdate upper = service.computeUpdate(1200, 1200, "WHITE");
        RatingService.EloUpdate lower = service.computeUpdate(1200, 1200, "white");

        assertEquals(upper.whiteDelta, lower.whiteDelta);
    }

    @Test
    public void testDeltasAreAlwaysZeroSumForEqualStartingElo() {
        RatingService.EloUpdate update = service.computeUpdate(1500, 1500, "BLACK");
        assertEquals(0, update.whiteDelta + update.blackDelta);
    }
}
