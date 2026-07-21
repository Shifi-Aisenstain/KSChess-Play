package server.dispatch.handlers;

import server.auth.AuthService;
import server.dispatch.MessageHandler;
import server.net.PlayerSession;
import shared.protocol.Message;
import shared.protocol.MessageCodec;
import shared.protocol.MessageType;
import shared.protocol.payload.LoginPayload;
import shared.protocol.payload.LoginResultPayload;

public final class LoginHandler implements MessageHandler {
    private final AuthService authService;

    public LoginHandler(AuthService authService) {
        this.authService = authService;
    }

    @Override
    public void handle(PlayerSession session, Message message) {
        LoginPayload payload = MessageCodec.decodePayload(message, LoginPayload.class);
        AuthService.Result result = authService.login(payload.username, payload.password);

        if (result.success) {
            session.setUser(result.user);
            session.send(MessageType.LOGIN_RESULT,
                    LoginResultPayload.ok(result.user.getId(), result.user.getUsername(), result.user.getElo()));
        } else {
            session.send(MessageType.LOGIN_RESULT, LoginResultPayload.fail(result.errorMessage));
        }
    }
}
