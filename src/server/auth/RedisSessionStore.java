package server.auth;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import redis.clients.jedis.Jedis;
import shared.redis.RedisConnectionProvider;

import java.util.Optional;
import java.util.UUID;

public final class RedisSessionStore implements SessionStore {
    private static final String KEY_PREFIX = "session:";
    private static final int TTL_SECONDS = 24 * 60 * 60;
    private static final Gson GSON = new GsonBuilder().create();

    private final RedisConnectionProvider connectionProvider;

    public RedisSessionStore(RedisConnectionProvider connectionProvider) {
        this.connectionProvider = connectionProvider;
    }

    @Override
    public String createSession(long userId, String username, int elo) {
        String token = UUID.randomUUID().toString();
        try (Jedis jedis = connectionProvider.getConnection()) {
            jedis.setex(KEY_PREFIX + token, TTL_SECONDS, GSON.toJson(new SessionInfo(userId, username, elo)));
        }
        return token;
    }

    @Override
    public Optional<SessionInfo> validate(String sessionToken) {
        if (sessionToken == null) return Optional.empty();
        try (Jedis jedis = connectionProvider.getConnection()) {
            String json = jedis.get(KEY_PREFIX + sessionToken);
            if (json == null) return Optional.empty();
            return Optional.of(GSON.fromJson(json, SessionInfo.class));
        }
    }
}
