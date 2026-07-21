package server.dispatch.handlers;

import server.auth.User;
import server.dispatch.MessageHandler;
import server.net.PlayerSession;
import server.rooms.RoomManager;
import shared.protocol.Message;

public final class RoomCancelHandler implements MessageHandler {
    private final RoomManager roomManager;

    public RoomCancelHandler(RoomManager roomManager) {
        this.roomManager = roomManager;
    }

    @Override
    public void handle(PlayerSession session, Message message) {
        User user = HandlerSupport.requireUser(session);
        if (user == null) return;

        String roomId = session.getCurrentRoomId();
        if (roomId != null) {
            roomManager.abandonRoom(roomId, user.getId());
            session.setCurrentRoomId(null);
        }
    }
}
