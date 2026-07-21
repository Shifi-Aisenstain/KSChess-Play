package server.auth;

/** Immutable snapshot of a persisted account. */
public final class User {
    private final long id;
    private final String username;
    private final int elo;

    public User(long id, String username, int elo) {
        this.id = id;
        this.username = username;
        this.elo = elo;
    }

    public long getId() { return id; }
    public String getUsername() { return username; }
    public int getElo() { return elo; }
}
