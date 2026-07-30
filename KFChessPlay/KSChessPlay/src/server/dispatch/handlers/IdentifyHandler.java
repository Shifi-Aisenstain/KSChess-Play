package server.dispatch.handlers;

import server.auth.User;
import server.dispatch.MessageHandler;
import server.gateway.GatewayRoomRegistry;
import server.gateway.GatewayRoomSession;
import server.gateway.RoomLifecycleClient;
import server.net.PlayerSession;
import shared.protocol.Message;
import shared.protocol.MessageCodec;
import shared.protocol.MessageType;
import shared.protocol.payload.IdentifyPayload;
import shared.protocol.payload.LoginResultPayload;

/**
 * Since PLAN_split_6_services.md §3.5, authentication itself happens over REST against the API
 * Gateway (see {@code server.apigateway.ApiGatewayMain}'s {@code POST /login}) before the client
 * even opens this socket - replacing the old {@code LoginHandler}, which checked a password directly.
 * This handler just attaches the identity the client already proved over REST to this connection's
 * {@link PlayerSession}; it has no {@code AuthService} dependency at all anymore. Reuses
 * {@code MessageType.LOGIN_RESULT}/{@link LoginResultPayload} for the ack, since from the client's
 * perspective this still completes the same "now I'm logged in on this connection" step.
 */
public final class IdentifyHandler implements MessageHandler {
    private final RoomLifecycleClient roomLifecycle;
    private final GatewayRoomRegistry roomRegistry;

    public IdentifyHandler(RoomLifecycleClient roomLifecycle, GatewayRoomRegistry roomRegistry) {
        this.roomLifecycle = roomLifecycle;
        this.roomRegistry = roomRegistry;
    }

    @Override
    public void handle(PlayerSession session, Message message) {
        IdentifyPayload payload = MessageCodec.decodePayload(message, IdentifyPayload.class);
        User user = new User(payload.userId, payload.username, payload.elo);

        session.setUser(user);
        reattachToActiveGameIfAny(session, user.getId());
        session.send(MessageType.LOGIN_RESULT, LoginResultPayload.ok(user.getId(), user.getUsername(), user.getElo()));
    }

    /**
     * Spec: "disconnection is not immediate loss - if you reconnect in time, the auto-resign timer
     * is cancelled." A fresh identify always creates a brand-new {@link PlayerSession} with no room,
     * so this is the one place that can notice "this user still has a game running" and quietly
     * resume it - asking the Allocator (over {@link RoomLifecycleClient#lookupActiveRoom}, since only
     * it still tracks that) instead of checking a local {@code Room} directly, then reattaching this
     * socket and cancelling any ticking {@code DisconnectWatchdog} - before the client even asks for one.
     */
    private void reattachToActiveGameIfAny(PlayerSession session, long userId) {
        roomLifecycle.lookupActiveRoom(userId).ifPresent(roomId -> {
            GatewayRoomSession roomSession = roomRegistry.attachToAlreadyRunningRoom(roomId, userId, session);
            if (roomSession.isOver()) return;
            session.setCurrentRoomId(roomId);
            roomSession.cancelWatchdogIfPresent(userId, roomSession.colorOf(userId));
        });
    }
}
