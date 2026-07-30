package server.dispatch.handlers;

import server.auth.User;
import server.dispatch.MessageHandler;
import server.gateway.RoomLifecycleClient;
import server.net.PlayerSession;
import shared.protocol.Message;

public final class RoomCancelHandler implements MessageHandler {
    private final RoomLifecycleClient roomLifecycle;

    public RoomCancelHandler(RoomLifecycleClient roomLifecycle) {
        this.roomLifecycle = roomLifecycle;
    }

    @Override
    public void handle(PlayerSession session, Message message) {
        User user = HandlerSupport.requireUser(session);
        if (user == null) return;

        String roomId = session.getCurrentRoomId();
        if (roomId != null) {
            roomLifecycle.cancelRoom(user.getId(), roomId);
            session.setCurrentRoomId(null);
        }
    }
}
