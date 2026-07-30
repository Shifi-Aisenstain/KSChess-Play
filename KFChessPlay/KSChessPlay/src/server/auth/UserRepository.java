package server.auth;

import java.util.Map;
import java.util.Optional;

/**
 * Repository pattern: isolates every other class in the server from SQL and
 * JDBC. {@link SqliteUserRepository} is the only class that knows this is
 * backed by SQLite - swapping storage engines later means writing one new
 * class, not touching AuthService/MatchmakingService/etc. (see also
 * {@code PostgresUserRepository}, Server_Design.md step 5).
 */
public interface UserRepository {
    Optional<StoredUser> findByUsername(String username);
    StoredUser create(String username, String passwordHash, String salt, int startingElo);
    void updateElo(long userId, int newElo);

    /**
     * Applies many Elo updates together (Server_Design.md step 5: {@code server.persistence.EloWriterMain}
     * drains a batch of Elo updates - one per game that just ended - on a timer rather than the Shard
     * writing synchronously on every single game). The default just loops {@link #updateElo} per
     * entry, so every existing implementation (and every test double implementing this interface)
     * keeps working unchanged; a real batch-capable implementation ({@code PostgresUserRepository})
     * should override this for one actual round-trip/transaction instead of N separate ones.
     */
    default void updateEloBatch(Map<Long, Integer> newEloByUserId) {
        newEloByUserId.forEach(this::updateElo);
    }

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
