package server.matchmaking;

import server.auth.User;

public interface RoomAllocationClient {

    String createMatchRoom(User white, User black);
}
