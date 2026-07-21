package server.dispatch.handlers;

import server.auth.User;
import server.dispatch.MessageHandler;
import server.net.PlayerSession;
import server.rooms.RoomManager;
import shared.protocol.Message;
import shared.protocol.MessageCodec;
import shared.protocol.MessageType;
import shared.protocol.payload.ErrorPayload;
import shared.protocol.payload.RoomActionPayload;
import shared.protocol.payload.RoomStatusPayload;

public final class RoomJoinHandler implements MessageHandler {
    private final RoomManager roomManager;

    public RoomJoinHandler(RoomManager roomManager) {
        this.roomManager = roomManager;
    }

    @Override
    public void handle(PlayerSession session, Message message) {
        User user = HandlerSupport.requireUser(session);
        if (user == null) return;

        RoomActionPayload payload = MessageCodec.decodePayload(message, RoomActionPayload.class);
        RoomManager.JoinResult result = roomManager.joinRoom(payload.roomId, user, session);

        if (!result.success) {
            session.send(MessageType.ERROR, new ErrorPayload(result.errorMessage));
            return;
        }
        session.setCurrentRoomId(result.room.getId());
        session.send(MessageType.ROOM_JOINED, new RoomStatusPayload(result.room.getId(), result.role.name()));
    }
}
