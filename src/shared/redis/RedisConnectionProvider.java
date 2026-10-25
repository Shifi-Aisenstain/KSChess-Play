package shared.redis;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

public final class RedisConnectionProvider {
    private final JedisPool pool;

    public RedisConnectionProvider(String redisUrl) {
        this.pool = new JedisPool(redisUrl);
    }

    public Jedis getConnection() {
        return pool.getResource();
    }

    public void close() {
        pool.close();
    }
}
