package server.matchmaking;

import com.google.gson.Gson;
import redis.clients.jedis.Jedis;
import shared.redis.RedisConnectionProvider;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class RedisWaitingPlayersQueue implements WaitingPlayersQueue {
    private static final String KEY = "matchmaking:waiting";
    private static final Gson GSON = new Gson();

    private final RedisConnectionProvider connectionProvider;

    public RedisWaitingPlayersQueue(RedisConnectionProvider connectionProvider) {
        this.connectionProvider = connectionProvider;
    }

    @Override
    public void addIfAbsent(WaitingPlayer player) {
        try (Jedis jedis = connectionProvider.getConnection()) {
            jedis.hsetnx(KEY, String.valueOf(player.userId()), GSON.toJson(player));
        }
    }

    @Override
    public boolean remove(long userId) {
        try (Jedis jedis = connectionProvider.getConnection()) {
            return jedis.hdel(KEY, String.valueOf(userId)) > 0;
        }
    }

    @Override
    public List<WaitingPlayer> snapshot() {
        try (Jedis jedis = connectionProvider.getConnection()) {
            Map<String, String> all = jedis.hgetAll(KEY);
            return all.values().stream()
                    .map(json -> GSON.fromJson(json, WaitingPlayer.class))
                    .collect(Collectors.toList());
        }
    }
}
