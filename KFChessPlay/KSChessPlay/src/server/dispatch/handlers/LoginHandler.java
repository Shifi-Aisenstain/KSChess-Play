package server.dispatch.handlers;

import server.auth.AuthService;
import server.dispatch.MessageHandler;
import server.game.GameSession;
import server.net.PlayerSession;
import server.rooms.RoomManager;
import shared.protocol.Message;
import shared.protocol.MessageCodec;
import shared.protocol.MessageType;
import shared.protocol.payload.LoginPayload;
import shared.protocol.payload.LoginResultPayload;

public final class LoginHandler implements MessageHandler {
    private final AuthService authService;
    private final RoomManager roomManager;

    public LoginHandler(AuthService authService, RoomManager roomManager) {
        this.authService = authService;
        this.roomManager = roomManager;
    }

    @Override
    public void handle(PlayerSession session, Message message) {
        LoginPayload payload = MessageCodec.decodePayload(message, LoginPayload.class);
        AuthService.Result result = authService.login(payload.username, payload.password);

        if (result.success) {
            session.setUser(result.user);
            reattachToActiveGameIfAny(session, result.user.getId());
            session.send(MessageType.LOGIN_RESULT,
                    LoginResultPayload.ok(result.user.getId(), result.user.getUsername(), result.user.getElo()));
        } else {
            session.send(MessageType.LOGIN_RESULT, LoginResultPayload.fail(result.errorMessage));
        }
    }

    /**
     * Spec: "disconnection is not immediate loss - if you reconnect in time, the auto-resign timer
     * is cancelled." A fresh login always creates a brand-new {@link PlayerSession} with no room, so
     * this is the one place that can notice "this user still has a game running" and quietly resume
     * it - reattaching their message sink and cancelling any ticking {@code DisconnectWatchdog} - before
     * the client even asks for one.
     */
    private void reattachToActiveGameIfAny(PlayerSession session, long userId) {
        roomManager.getActiveRoomForUser(userId).ifPresent(roomId ->
                roomManager.getRoom(roomId).ifPresent(room -> {
                    GameSession gameSession = room.getGameSession();
                    if (gameSession == null || gameSession.isOver()) return;
                    room.attachSink(userId, session);
                    session.setCurrentRoomId(roomId);
                    gameSession.cancelWatchdogIfPresent(userId);
                }));
    }
}
