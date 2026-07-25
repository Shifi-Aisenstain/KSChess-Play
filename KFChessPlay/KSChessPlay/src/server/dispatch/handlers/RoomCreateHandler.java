package server.dispatch.handlers;

import server.auth.User;
import server.dispatch.MessageHandler;
import server.net.PlayerSession;
import server.rooms.Room;
import server.rooms.RoomManager;
import shared.protocol.Message;
import shared.protocol.MessageCodec;
import shared.protocol.MessageType;
import shared.protocol.payload.ErrorPayload;
import shared.protocol.payload.RoomActionPayload;
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

        RoomActionPayload payload = MessageCodec.decodePayload(message, RoomActionPayload.class);
        RoomManager.CreateResult result = roomManager.createRoom(user, session, payload.roomId);
        if (!result.success) {
            session.send(MessageType.ERROR, new ErrorPayload(result.errorMessage));
            return;
        }

        session.setCurrentRoomId(result.room.getId());
        session.send(MessageType.ROOM_CREATED, new RoomStatusPayload(result.room.getId(), Room.Role.WHITE.name()));
    }
}
