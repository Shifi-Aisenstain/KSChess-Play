package shared.redis;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

/**
 * Single point that knows how to obtain a pooled Redis connection (Server_Design.md step 4) - mirrors
 * {@code server.persistence.SqliteConnectionProvider}'s role for SQLite: configured exactly once,
 * injected into whatever needs a {@link Jedis} resource, rather than each caller managing its own pool.
 */
public final class RedisConnectionProvider {
    private final JedisPool pool;

    public RedisConnectionProvider(String redisUrl) {
        this.pool = new JedisPool(redisUrl);
    }

    /** Caller must {@code close()} the returned {@link Jedis} (try-with-resources) to return it to
     *  the pool - it does not close the underlying connection. */
    public Jedis getConnection() {
        return pool.getResource();
    }

    public void close() {
        pool.close();
    }
}
