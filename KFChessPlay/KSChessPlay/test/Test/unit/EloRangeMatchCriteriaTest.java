package Test.unit;

import org.junit.jupiter.api.Test;
import server.matchmaking.EloRangeMatchCriteria;
import server.matchmaking.MatchListener;
import server.matchmaking.MatchRequest;

import static org.junit.jupiter.api.Assertions.*;

public class EloRangeMatchCriteriaTest {

    private static final class NoOpMatchListener implements MatchListener {
        @Override public void onMatchFound(String roomId, char assignedColor, String opponentUsername, int opponentElo) { }
        @Override public void onNotFound() { }
    }

    private final EloRangeMatchCriteria criteria = new EloRangeMatchCriteria(100);

    private MatchRequest request(long userId, int elo) {
        return new MatchRequest(userId, "user" + userId, elo, new NoOpMatchListener());
    }

    @Test
    public void testMatchesWithinRange() {
        assertTrue(criteria.matches(request(1, 1200), request(2, 1280)));
    }

    @Test
    public void testMatchesAtExactRangeBoundary() {
        assertTrue(criteria.matches(request(1, 1200), request(2, 1300)));
    }

    @Test
    public void testDoesNotMatchOutsideRange() {
        assertFalse(criteria.matches(request(1, 1200), request(2, 1301)));
    }

    @Test
    public void testDoesNotMatchSameUser() {
        assertFalse(criteria.matches(request(1, 1200), request(1, 1200)));
    }

    @Test
    public void testOrderOfArgumentsDoesNotMatter() {
        MatchRequest a = request(1, 1000);
        MatchRequest b = request(2, 1050);
        assertEquals(criteria.matches(a, b), criteria.matches(b, a));
    }
}
