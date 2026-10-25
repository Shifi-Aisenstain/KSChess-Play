package server.allocator;

import java.util.Optional;

public interface ActiveRoomRegistry {
    Optional<String> get(long userId);

    void put(long userId, String roomId);

    void remove(long userId);
}
