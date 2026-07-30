package server.dispatch.handlers;

import server.auth.User;
import server.dispatch.MessageHandler;
import server.gateway.MatchmakingClient;
import server.net.PlayerSession;
import shared.protocol.Message;

public final class PlayCancelHandler implements MessageHandler {
    private final MatchmakingClient matchmakingClient;

    public PlayCancelHandler(MatchmakingClient matchmakingClient) {
        this.matchmakingClient = matchmakingClient;
    }

    @Override
    public void handle(PlayerSession session, Message message) {
        User user = HandlerSupport.requireUser(session);
        if (user == null) return;
        matchmakingClient.cancel(user.getId());
    }
}
