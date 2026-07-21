package server.dispatch.handlers;

import server.dispatch.MessageHandler;
import server.game.GameSession;
import server.net.PlayerSession;
import server.rooms.RoomManager;
import shared.protocol.AlgebraicNotation;
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
        AlgebraicNotation.ParsedCommand parsed;
        try {
            parsed = AlgebraicNotation.parse(payload.command, gameSession.getBoardRows());
        } catch (RuntimeException e) {
            session.send(MessageType.ERROR, new ErrorPayload("Malformed move command: " + payload.command));
            return;
        }

        boolean accepted = gameSession.handleMove(session.getUser().getId(), parsed.from, parsed.to);
        if (!accepted) {
            session.send(MessageType.ERROR, new ErrorPayload("Move rejected - not your piece, or square is invalid."));
        }
    }
}
