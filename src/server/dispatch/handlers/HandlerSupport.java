package server.dispatch.handlers;

import server.auth.User;
import server.gateway.GatewayRoomRegistry;
import server.gateway.GatewayRoomSession;
import server.net.PlayerSession;
import shared.protocol.MessageType;
import shared.protocol.payload.ErrorPayload;

final class HandlerSupport {
    private HandlerSupport() { }

    static User requireUser(PlayerSession session) {
        User user = session.getUser();
        if (user == null) {
            session.send(MessageType.ERROR, new ErrorPayload("You must log in first."));
        }
        return user;
    }

    static GatewayRoomSession requireActiveGame(GatewayRoomRegistry registry, PlayerSession session) {
        String roomId = session.getCurrentRoomId();
        if (roomId == null) {
            session.send(MessageType.ERROR, new ErrorPayload("You are not in a room."));
            return null;
        }
        GatewayRoomSession roomSession = registry.get(roomId).orElse(null);
        if (roomSession == null || roomSession.isOver()) {
            session.send(MessageType.ERROR, new ErrorPayload("Game has not started yet."));
            return null;
        }
        return roomSession;
    }
}
