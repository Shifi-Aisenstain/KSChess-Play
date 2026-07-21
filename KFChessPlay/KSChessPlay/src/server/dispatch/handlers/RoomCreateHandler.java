package server.dispatch.handlers;

import server.auth.User;
import server.dispatch.MessageHandler;
import server.net.PlayerSession;
import server.rooms.Room;
import server.rooms.RoomManager;
import shared.protocol.Message;
import shared.protocol.MessageType;
import shared.protocol.payload.RoomStatusPayload;

public final class RoomCreateHandler implements MessageHandler {
    private final RoomManager roomManager;

    public RoomCreateHandler(RoomManager roomManager) {
        this.roomManager = roomManager;
    }

    @Override
    public void handle(PlayerSession session, Message message) {
        User user = HandlerSupport.requireUser(session);
        if (user == null) return;

        Room room = roomManager.createRoom(user, session);
        session.setCurrentRoomId(room.getId());
        session.send(MessageType.ROOM_CREATED, new RoomStatusPayload(room.getId(), Room.Role.WHITE.name()));
    }
}
