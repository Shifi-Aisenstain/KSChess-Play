package server.dispatch.handlers;

import server.dispatch.MessageHandler;
import server.game.GameSession;
import server.net.PlayerSession;
import server.rooms.RoomManager;
import shared.protocol.Message;

public final class ResignHandler implements MessageHandler {
    private final RoomManager roomManager;

    public ResignHandler(RoomManager roomManager) {
        this.roomManager = roomManager;
    }

    @Override
    public void handle(PlayerSession session, Message message) {
        if (HandlerSupport.requireUser(session) == null) return;
        GameSession gameSession = HandlerSupport.requireActiveGame(roomManager, session);
        if (gameSession == null) return;

        gameSession.handleResign(session.getUser().getId());
    }
}
