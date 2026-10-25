package server.dispatch.handlers;

import models.Position;
import server.dispatch.MessageHandler;
import server.gateway.GatewayRoomRegistry;
import server.gateway.GatewayRoomSession;
import server.net.PlayerSession;
import shared.protocol.Message;
import shared.protocol.MessageCodec;
import shared.protocol.payload.SelectPayload;

public final class SelectHandler implements MessageHandler {
    private final GatewayRoomRegistry roomRegistry;

    public SelectHandler(GatewayRoomRegistry roomRegistry) {
        this.roomRegistry = roomRegistry;
    }

    @Override
    public void handle(PlayerSession session, Message message) {
        if (HandlerSupport.requireUser(session) == null) return;
        GatewayRoomSession roomSession = HandlerSupport.requireActiveGame(roomRegistry, session);
        if (roomSession == null) return;

        SelectPayload payload = MessageCodec.decodePayload(message, SelectPayload.class);
        Position position = payload.selected ? new Position(payload.row, payload.col) : null;
        roomSession.handleSelect(session.getUser().getId(), position);
    }
}
