package server.allocator;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryActiveRoomRegistry implements ActiveRoomRegistry {
    private final Map<Long, String> byUserId = new ConcurrentHashMap<>();

    @Override
    public Optional<String> get(long userId) {
        return Optional.ofNullable(byUserId.get(userId));
    }

    @Override
    public void put(long userId, String roomId) {
        byUserId.put(userId, roomId);
    }

    @Override
    public void remove(long userId) {
        byUserId.remove(userId);
    }
}
