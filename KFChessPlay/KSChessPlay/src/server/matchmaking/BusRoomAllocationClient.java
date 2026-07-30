package server.matchmaking;

import server.auth.User;
import server.gateway.RoomLifecycleClient;

public final class BusRoomAllocationClient implements RoomAllocationClient {
    private final RoomLifecycleClient roomLifecycle;

    public BusRoomAllocationClient(RoomLifecycleClient roomLifecycle) {
        this.roomLifecycle = roomLifecycle;
    }

    @Override
    public String createMatchRoom(User white, User black) {
        RoomLifecycleClient.Result created =
                roomLifecycle.createRoom(white.getId(), white.getUsername(), white.getElo(), null);
        if (!created.success) return null;

        RoomLifecycleClient.Result joined =
                roomLifecycle.joinRoom(black.getId(), black.getUsername(), black.getElo(), created.roomId);
        return joined.success ? created.roomId : null;
    }
}
