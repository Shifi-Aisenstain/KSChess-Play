package server.matchmaking;

/** Pairs players whose ELO differs by at most {@code range} (spec: +-100). */
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
