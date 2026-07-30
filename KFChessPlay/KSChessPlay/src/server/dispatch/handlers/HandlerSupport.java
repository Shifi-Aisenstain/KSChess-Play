package server.dispatch.handlers;

import server.auth.User;
import server.gateway.GatewayRoomRegistry;
import server.gateway.GatewayRoomSession;
import server.net.PlayerSession;
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

    /** Replaces the old {@code RoomManager}-backed lookup (PLAN_split_6_services.md §3.2): gameplay
     *  handlers on this Gateway instance no longer reach a live {@code GameSessionHandle} at all -
     *  they reach this room's {@link GatewayRoomSession} instead, which relays straight to the Shard
     *  over the bus. {@code isOver()} is still checked here so a stray command against an already-
     *  finished game gets the same "not started/not active" error a real live room would have given. */
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
