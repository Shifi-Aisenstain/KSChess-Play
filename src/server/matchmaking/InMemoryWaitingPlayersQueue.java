package server.matchmaking;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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
