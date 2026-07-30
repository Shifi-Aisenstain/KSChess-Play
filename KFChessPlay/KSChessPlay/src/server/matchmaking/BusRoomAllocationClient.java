package server.matchmaking;

import server.auth.User;
import server.gateway.RoomLifecycleClient;

/**
 * Bridges {@link MatchmakingServiceImpl} to the Game Allocator process over the bus
 * (PLAN_split_6_services.md §3.3): seats a matched pair by reusing the exact same create+join round
 * trip a manually-created room already goes through - create as White (no desired room id, so one is
 * generated), then join as Black - rather than inventing a matchmaking-only subject/payload. A fresh
 * room with White already seated is exactly what {@link RoomLifecycleClient#createRoom} produces, and
 * joining as the second color is exactly what already triggers game-session allocation (see
 * {@code RoomManager.joinRoom}), so no new wire contract is needed on the Allocator's side at all.
 */
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
