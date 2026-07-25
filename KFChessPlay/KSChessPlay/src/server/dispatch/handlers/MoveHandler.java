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
import shared.protocol.payload.MoveCommandPayload;

public final class MoveHandler implements MessageHandler {
    private final RoomManager roomManager;

    public MoveHandler(RoomManager roomManager) {
        this.roomManager = roomManager;
    }

    @Override
    public void handle(PlayerSession session, Message message) {
        if (HandlerSupport.requireUser(session) == null) return;
        GameSession gameSession = HandlerSupport.requireActiveGame(roomManager, session);
        if (gameSession == null) return;

        MoveCommandPayload payload = MessageCodec.decodePayload(message, MoveCommandPayload.class);
        Position from = new Position(payload.fromRow, payload.fromCol);
        Position to = new Position(payload.toRow, payload.toCol);

        boolean accepted = gameSession.handleMove(session.getUser().getId(), from, to);
        if (!accepted) {
            session.send(MessageType.ERROR, new ErrorPayload("Move rejected - not your piece, or square is invalid."));
        }
    }
}
