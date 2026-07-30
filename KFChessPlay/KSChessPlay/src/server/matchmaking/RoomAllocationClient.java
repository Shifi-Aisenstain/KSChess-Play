package server.matchmaking;

import server.auth.User;

/**
 * Abstraction {@link MatchmakingServiceImpl} uses to seat a matched pair into a room, without
 * depending on how/where that room actually lives - a bus round trip to the Game Allocator process
 * since PLAN_split_6_services.md §3.3 (see {@link BusRoomAllocationClient}), replacing the in-process
 * {@code RoomManager.createRoomForMatch} call this used to make directly before the split.
 */
public interface RoomAllocationClient {
    /** @return the created room's id, or {@code null} if allocation failed (e.g. the Allocator didn't
     *  respond in time) - callers should treat that the same as "no match found". */
    String createMatchRoom(User white, User black);
}
