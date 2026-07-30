package server.dispatch.handlers;

import server.auth.SessionInfo;
import server.auth.SessionStore;
import server.auth.User;
import server.dispatch.MessageHandler;
import server.gateway.GatewayRoomRegistry;
import server.gateway.GatewayRoomSession;
import server.gateway.RoomLifecycleClient;
import server.net.PlayerSession;
import shared.metrics.MetricsRegistry;
import shared.protocol.Message;
import shared.protocol.MessageCodec;
import shared.protocol.MessageType;
import shared.protocol.payload.ErrorPayload;
import shared.protocol.payload.IdentifyPayload;
import shared.protocol.payload.LoginResultPayload;

import java.util.Optional;

public final class IdentifyHandler implements MessageHandler {
    private final RoomLifecycleClient roomLifecycle;
    private final GatewayRoomRegistry roomRegistry;
    private final SessionStore sessionStore;
    private final MetricsRegistry metricsRegistry;

    public IdentifyHandler(RoomLifecycleClient roomLifecycle, GatewayRoomRegistry roomRegistry, SessionStore sessionStore,
                            MetricsRegistry metricsRegistry) {
        this.roomLifecycle = roomLifecycle;
        this.roomRegistry = roomRegistry;
        this.sessionStore = sessionStore;
        this.metricsRegistry = metricsRegistry;
    }

    @Override
    public void handle(PlayerSession session, Message message) {
        IdentifyPayload payload = MessageCodec.decodePayload(message, IdentifyPayload.class);
        Optional<SessionInfo> sessionInfo = sessionStore.validate(payload.sessionToken);
        if (sessionInfo.isEmpty()) {
            metricsRegistry.incrementCounter("gateway_identify_failures_total", "Total IDENTIFY attempts rejected for an invalid or expired session token");
            session.send(MessageType.ERROR, new ErrorPayload("Invalid or expired session - please log in again."));
            return;
        }

        SessionInfo info = sessionInfo.get();
        User user = new User(info.userId, info.username, info.elo);

        session.setUser(user);
        reattachToActiveGameIfAny(session, user.getId());
        metricsRegistry.incrementCounter("gateway_identify_success_total", "Total IDENTIFY attempts accepted with a valid session token");
        session.send(MessageType.LOGIN_RESULT,
                LoginResultPayload.ok(user.getId(), user.getUsername(), user.getElo(), payload.sessionToken));
    }

    private void reattachToActiveGameIfAny(PlayerSession session, long userId) {
        roomLifecycle.lookupActiveRoom(userId).ifPresent(roomId -> {
            GatewayRoomSession roomSession = roomRegistry.attachToAlreadyRunningRoom(roomId, userId, session);
            if (roomSession.isOver()) return;
            session.setCurrentRoomId(roomId);
            roomSession.cancelWatchdogIfPresent(userId, roomSession.colorOf(userId));
        });
    }
}
