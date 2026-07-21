package server.matchmaking;

/**
 * Strategy pattern: decides whether two waiting players are an acceptable
 * pairing. Isolated behind an interface so the matching rule (currently
 * "ELO within +-100") can be swapped or made adaptive without touching the
 * queue-management code in {@link MatchmakingServiceImpl}.
 */
public interface MatchCriteria {
    boolean matches(MatchRequest a, MatchRequest b);
}
