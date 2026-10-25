package server.auth;

import java.util.Map;
import java.util.Optional;

public interface UserRepository {
    Optional<StoredUser> findByUsername(String username);
    StoredUser create(String username, String passwordHash, String salt, int startingElo);
    void updateElo(long userId, int newElo);

    default void updateEloBatch(Map<Long, Integer> newEloByUserId) {
        newEloByUserId.forEach(this::updateElo);
    }

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
