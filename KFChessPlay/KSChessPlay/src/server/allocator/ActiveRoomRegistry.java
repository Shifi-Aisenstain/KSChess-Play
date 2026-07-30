package server.allocator;

import java.util.Optional;

/**
 * Abstraction over where "which room is this user currently in" lives (Server_Design.md step 4:
 * shared session/presence, needed once there's more than one process) - what {@code RoomManager}
 * used to keep as a private in-process map. Needs to be visible across Allocator restarts/replicas so
 * a reconnect lookup ({@code server.gateway.RoomLifecycleClient#lookupActiveRoom}) still works
 * correctly no matter which Allocator instance answers it.
 */
public interface ActiveRoomRegistry {
    Optional<String> get(long userId);

    void put(long userId, String roomId);

    void remove(long userId);
}
