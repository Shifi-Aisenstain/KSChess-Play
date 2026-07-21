package server.dispatch.handlers;

import server.auth.User;
import server.dispatch.MessageHandler;
import server.matchmaking.MatchmakingService;
import server.net.PlayerSession;
import shared.protocol.Message;

public final class PlayCancelHandler implements MessageHandler {
    private final MatchmakingService matchmakingService;

    public PlayCancelHandler(MatchmakingService matchmakingService) {
        this.matchmakingService = matchmakingService;
    }

    @Override
    public void handle(PlayerSession session, Message message) {
        User user = HandlerSupport.requireUser(session);
        if (user == null) return;
        matchmakingService.cancel(user.getId());
    }
}
