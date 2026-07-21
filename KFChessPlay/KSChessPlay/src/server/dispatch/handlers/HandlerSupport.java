package server.dispatch.handlers;

import server.auth.User;
import server.game.GameSession;
import server.net.PlayerSession;
import server.rooms.Room;
import server.rooms.RoomManager;
import shared.protocol.MessageType;
import shared.protocol.payload.ErrorPayload;

/** Small shared helpers so every handler doesn't re-implement the same guard clauses. */
final class HandlerSupport {
    private HandlerSupport() { }

    static User requireUser(PlayerSession session) {
        User user = session.getUser();
        if (user == null) {
            session.send(MessageType.ERROR, new ErrorPayload("You must log in first."));
        }
        return user;
    }

    static GameSession requireActiveGame(RoomManager roomManager, PlayerSession session) {
        String roomId = session.getCurrentRoomId();
        if (roomId == null) {
            session.send(MessageType.ERROR, new ErrorPayload("You are not in a room."));
            return null;
        }
        Room room = roomManager.getRoom(roomId).orElse(null);
        if (room == null || room.getGameSession() == null) {
            session.send(MessageType.ERROR, new ErrorPayload("Game has not started yet."));
            return null;
        }
        return room.getGameSession();
    }
}
