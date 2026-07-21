package server.auth;

import java.util.Optional;

/**
 * Repository pattern: isolates every other class in the server from SQL and
 * JDBC. {@link SqliteUserRepository} is the only class that knows this is
 * backed by SQLite - swapping storage engines later means writing one new
 * class, not touching AuthService/MatchmakingService/etc.
 */
public interface UserRepository {
    Optional<StoredUser> findByUsername(String username);
    StoredUser create(String username, String passwordHash, String salt, int startingElo);
    void updateElo(long userId, int newElo);

    /** Internal record including credential material - never leaves the auth package. */
    final class StoredUser {
        public final long id;
        public final String username;
        public final String passwordHash;
        public final String salt;
        public final int elo;

        public StoredUser(long id, String username, String passwordHash, String salt, int elo) {
            this.id = id;
            this.username = username;
            this.passwordHash = passwordHash;
            this.salt = salt;
            this.elo = elo;
        }
    }
}
