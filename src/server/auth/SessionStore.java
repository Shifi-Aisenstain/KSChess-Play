package server.auth;

import java.util.Optional;

public interface SessionStore {
    String createSession(long userId, String username, int elo);

    Optional<SessionInfo> validate(String sessionToken);
}
