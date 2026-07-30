package server.dispatch.handlers;

import server.auth.User;
import server.dispatch.MessageHandler;
import server.gateway.GatewayRoomRegistry;
import server.gateway.GatewayRoomSession;
import server.gateway.RoomLifecycleClient;
import server.net.PlayerSession;
import server.rooms.Room;
import shared.protocol.Message;
import shared.protocol.MessageCodec;
import shared.protocol.MessageType;
import shared.protocol.payload.ErrorPayload;
import shared.protocol.payload.RoomActionPayload;
import shared.protocol.payload.RoomStatusPayload;

import java.util.Optional;

public final class RoomJoinHandler implements MessageHandler {
    private final RoomLifecycleClient roomLifecycle;
    private final GatewayRoomRegistry roomRegistry;

    public RoomJoinHandler(RoomLifecycleClient roomLifecycle, GatewayRoomRegistry roomRegistry) {
        this.roomLifecycle = roomLifecycle;
        this.roomRegistry = roomRegistry;
    }

    @Override
    public void handle(PlayerSession session, Message message) {
        User user = HandlerSupport.requireUser(session);
        if (user == null) return;

        RoomActionPayload payload = MessageCodec.decodePayload(message, RoomActionPayload.class);
        RoomLifecycleClient.Result result = roomLifecycle.joinRoom(user.getId(), user.getUsername(), user.getElo(), payload.roomId);

        if (!result.success) {
            session.send(MessageType.ERROR, new ErrorPayload(result.errorMessage));
            return;
        }
        session.setCurrentRoomId(result.roomId);

        Optional<GatewayRoomSession> attached = roomRegistry.registerPendingMember(result.roomId, user.getId(), session);

        if (Room.Role.SPECTATOR.name().equals(result.role)) {
            attached.ifPresent(roomSession -> roomSession.notifyMemberJoined(user.getId(), Room.Role.SPECTATOR));
        }

        session.send(MessageType.ROOM_JOINED, new RoomStatusPayload(result.roomId, result.role));
    }
}
