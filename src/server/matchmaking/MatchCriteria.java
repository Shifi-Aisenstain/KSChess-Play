package server.matchmaking;

public interface MatchCriteria {
    boolean matches(MatchRequest a, MatchRequest b);
}
