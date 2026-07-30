package server.allocator;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Default/test double for {@link ActiveRoomRegistry} - correct for one process, but (unlike
 * {@link RedisActiveRoomRegistry}) doesn't survive a restart or a second Allocator instance. Fine for
 * today's single-Allocator-replica reality (see docker-compose.yml) and for tests - functionally
 * identical to {@code RoomManager}'s pre-step-4 in-process {@code activeRoomByUserId} map.
 */
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
