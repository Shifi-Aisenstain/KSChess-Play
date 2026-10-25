package server.dispatch.handlers;

import server.dispatch.MessageHandler;
import server.gateway.GatewayRoomRegistry;
import server.gateway.GatewayRoomSession;
import server.net.PlayerSession;
import shared.protocol.Message;

public final class ResignHandler implements MessageHandler {
    private final GatewayRoomRegistry roomRegistry;

    public ResignHandler(GatewayRoomRegistry roomRegistry) {
        this.roomRegistry = roomRegistry;
    }

    @Override
    public void handle(PlayerSession session, Message message) {
        if (HandlerSupport.requireUser(session) == null) return;
        GatewayRoomSession roomSession = HandlerSupport.requireActiveGame(roomRegistry, session);
        if (roomSession == null) return;

        roomSession.handleResign(session.getUser().getId());
    }
}
