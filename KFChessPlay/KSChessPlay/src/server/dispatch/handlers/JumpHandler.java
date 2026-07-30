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
import shared.protocol.payload.JumpCommandPayload;

public final class JumpHandler implements MessageHandler {
    private final GatewayRoomRegistry roomRegistry;

    public JumpHandler(GatewayRoomRegistry roomRegistry) {
        this.roomRegistry = roomRegistry;
    }

    @Override
    public void handle(PlayerSession session, Message message) {
        if (HandlerSupport.requireUser(session) == null) return;
        GatewayRoomSession roomSession = HandlerSupport.requireActiveGame(roomRegistry, session);
        if (roomSession == null) return;

        JumpCommandPayload payload = MessageCodec.decodePayload(message, JumpCommandPayload.class);
        boolean accepted = roomSession.handleJump(session.getUser().getId(), new Position(payload.row, payload.col));
        if (!accepted) {
            session.send(MessageType.ERROR, new ErrorPayload("Jump rejected - not your piece, or square is invalid."));
        }
    }
}
