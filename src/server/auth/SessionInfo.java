package server.auth;

public final class SessionInfo {
    public final long userId;
    public final String username;
    public final int elo;

    public SessionInfo(long userId, String username, int elo) {
        this.userId = userId;
        this.username = username;
        this.elo = elo;
    }
}
