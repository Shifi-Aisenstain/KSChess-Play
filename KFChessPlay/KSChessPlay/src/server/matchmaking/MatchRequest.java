package server.matchmaking;

public final class MatchRequest {
    public final long userId;
    public final String username;
    public final int elo;
    public final MatchListener listener;
    public final long enqueuedAtMs;

    public MatchRequest(long userId, String username, int elo, MatchListener listener) {
        this.userId = userId;
        this.username = username;
        this.elo = elo;
        this.listener = listener;
        this.enqueuedAtMs = System.currentTimeMillis();
    }
}
