package server.dispatch.handlers;

import models.Position;
import server.dispatch.MessageHandler;
import server.game.GameSession;
import server.net.PlayerSession;
import server.rooms.RoomManager;
import shared.protocol.Message;
import shared.protocol.MessageCodec;
import shared.protocol.payload.SelectPayload;

/** Server-authoritative click-to-select, so legal-move highlighting never has to trust the client. */
public final class SelectHandler implements MessageHandler {
    private final RoomManager roomManager;

    public SelectHandler(RoomManager roomManager) {
        this.roomManager = roomManager;
    }

    @Override
    public void handle(PlayerSession session, Message message) {
        if (HandlerSupport.requireUser(session) == null) return;
        GameSession gameSession = HandlerSupport.requireActiveGame(roomManager, session);
        if (gameSession == null) return;

        SelectPayload payload = MessageCodec.decodePayload(message, SelectPayload.class);
        Position position = payload.selected ? new Position(payload.row, payload.col) : null;
        gameSession.handleSelect(session.getUser().getId(), position);
    }
}
