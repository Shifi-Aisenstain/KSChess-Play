package server.matchmaking;

public final class EloRangeMatchCriteria implements MatchCriteria {
    private final int range;

    public EloRangeMatchCriteria(int range) {
        this.range = range;
    }

    @Override
    public boolean matches(MatchRequest a, MatchRequest b) {
        return a.userId != b.userId && Math.abs(a.elo - b.elo) <= range;
    }
}
