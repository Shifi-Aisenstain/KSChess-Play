package server.matchmaking;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Default/test double for {@link WaitingPlayersQueue} - correct for any number of threads within one
 * process, but (unlike {@link RedisWaitingPlayersQueue}) not actually shared across processes. Fine
 * for today's single-Matchmaker-replica reality (see docker-compose.yml) and for tests - functionally
 * identical to {@code MatchmakingServiceImpl}'s pre-step-4 in-process queue.
 */
public final class InMemoryWaitingPlayersQueue implements WaitingPlayersQueue {
    private final Map<Long, WaitingPlayer> byUserId = new ConcurrentHashMap<>();

    @Override
    public void addIfAbsent(WaitingPlayer player) {
        byUserId.putIfAbsent(player.userId(), player);
    }

    @Override
    public boolean remove(long userId) {
        return byUserId.remove(userId) != null;
    }

    @Override
    public List<WaitingPlayer> snapshot() {
        return List.copyOf(byUserId.values());
    }
}
