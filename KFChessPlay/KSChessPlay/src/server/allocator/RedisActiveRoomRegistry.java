package server.allocator;

import redis.clients.jedis.Jedis;
import shared.redis.RedisConnectionProvider;

import java.util.Optional;

/**
 * Real {@link ActiveRoomRegistry} backed by a single Redis hash (Server_Design.md step 4) - field is
 * the userId, value is the roomId. Plain strings, no serialization needed.
 */
public final class RedisActiveRoomRegistry implements ActiveRoomRegistry {
    private static final String KEY = "allocator:active-room";

    private final RedisConnectionProvider connectionProvider;

    public RedisActiveRoomRegistry(RedisConnectionProvider connectionProvider) {
        this.connectionProvider = connectionProvider;
    }

    @Override
    public Optional<String> get(long userId) {
        try (Jedis jedis = connectionProvider.getConnection()) {
            return Optional.ofNullable(jedis.hget(KEY, String.valueOf(userId)));
        }
    }

    @Override
    public void put(long userId, String roomId) {
        try (Jedis jedis = connectionProvider.getConnection()) {
            jedis.hset(KEY, String.valueOf(userId), roomId);
        }
    }

    @Override
    public void remove(long userId) {
        try (Jedis jedis = connectionProvider.getConnection()) {
            jedis.hdel(KEY, String.valueOf(userId));
        }
    }
}
