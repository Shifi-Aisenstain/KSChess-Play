package server.dispatch.handlers;

import models.Position;
import server.dispatch.MessageHandler;
import server.gateway.GatewayRoomRegistry;
import server.gateway.GatewayRoomSession;
import server.net.PlayerSession;
import shared.protocol.Message;
import shared.protocol.MessageCodec;
import shared.protocol.MessageType;
import shared.protocol.payload.ErrorPayload;
import shared.protocol.payload.MoveCommandPayload;

public final class MoveHandler implements MessageHandler {
    private final GatewayRoomRegistry roomRegistry;

    public MoveHandler(GatewayRoomRegistry roomRegistry) {
        this.roomRegistry = roomRegistry;
    }

    @Override
    public void handle(PlayerSession session, Message message) {
        if (HandlerSupport.requireUser(session) == null) return;
        GatewayRoomSession roomSession = HandlerSupport.requireActiveGame(roomRegistry, session);
        if (roomSession == null) return;

        MoveCommandPayload payload = MessageCodec.decodePayload(message, MoveCommandPayload.class);
        Position from = new Position(payload.fromRow, payload.fromCol);
        Position to = new Position(payload.toRow, payload.toCol);

        boolean accepted = roomSession.handleMove(session.getUser().getId(), from, to);
        if (!accepted) {
            session.send(MessageType.ERROR, new ErrorPayload("Move rejected - not your piece, or square is invalid."));
        }
    }
}
