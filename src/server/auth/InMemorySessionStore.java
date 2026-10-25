package server.auth;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemorySessionStore implements SessionStore {
    private static final long DEFAULT_TTL_MS = 24L * 60 * 60 * 1000;

    private final Map<String, Entry> sessionsByToken = new ConcurrentHashMap<>();
    private final long ttlMs;

    public InMemorySessionStore() {
        this(DEFAULT_TTL_MS);
    }

    public InMemorySessionStore(long ttlMs) {
        this.ttlMs = ttlMs;
    }

    @Override
    public String createSession(long userId, String username, int elo) {
        String token = UUID.randomUUID().toString();
        sessionsByToken.put(token, new Entry(new SessionInfo(userId, username, elo), System.currentTimeMillis() + ttlMs));
        return token;
    }

    @Override
    public Optional<SessionInfo> validate(String sessionToken) {
        if (sessionToken == null) return Optional.empty();
        Entry entry = sessionsByToken.get(sessionToken);
        if (entry == null) return Optional.empty();
        if (System.currentTimeMillis() > entry.expiresAtMs) {
            sessionsByToken.remove(sessionToken);
            return Optional.empty();
        }
        return Optional.of(entry.info);
    }

    private static final class Entry {
        final SessionInfo info;
        final long expiresAtMs;

        Entry(SessionInfo info, long expiresAtMs) {
            this.info = info;
            this.expiresAtMs = expiresAtMs;
        }
    }
}
