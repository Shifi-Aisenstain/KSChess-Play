package server.dispatch.handlers;

import models.Position;
import server.dispatch.MessageHandler;
import server.game.GameSession;
import server.net.PlayerSession;
import server.rooms.RoomManager;
import shared.protocol.Message;
import shared.protocol.MessageCodec;
import shared.protocol.MessageType;
import shared.protocol.payload.ErrorPayload;
import shared.protocol.payload.JumpCommandPayload;

public final class JumpHandler implements MessageHandler {
    private final RoomManager roomManager;

    public JumpHandler(RoomManager roomManager) {
        this.roomManager = roomManager;
    }

    @Override
    public void handle(PlayerSession session, Message message) {
        if (HandlerSupport.requireUser(session) == null) return;
        GameSession gameSession = HandlerSupport.requireActiveGame(roomManager, session);
        if (gameSession == null) return;

        JumpCommandPayload payload = MessageCodec.decodePayload(message, JumpCommandPayload.class);
        boolean accepted = gameSession.handleJump(session.getUser().getId(), new Position(payload.row, payload.col));
        if (!accepted) {
            session.send(MessageType.ERROR, new ErrorPayload("Jump rejected - not your piece, or square is invalid."));
        }
    }
}
