package server.dispatch.handlers;

import server.auth.User;
import server.dispatch.MessageHandler;
import server.matchmaking.MatchListener;
import server.matchmaking.MatchRequest;
import server.matchmaking.MatchmakingService;
import server.net.PlayerSession;
import server.rooms.RoomManager;
import shared.protocol.Message;
import shared.protocol.MessageType;
import shared.protocol.payload.EmptyPayload;
import shared.protocol.payload.ErrorPayload;
import shared.protocol.payload.MatchFoundPayload;

public final class PlayRequestHandler implements MessageHandler {
    private final MatchmakingService matchmakingService;
    private final RoomManager roomManager;

    public PlayRequestHandler(MatchmakingService matchmakingService, RoomManager roomManager) {
        this.matchmakingService = matchmakingService;
        this.roomManager = roomManager;
    }

    @Override
    public void handle(PlayerSession session, Message message) {
        User user = HandlerSupport.requireUser(session);
        if (user == null) return;

        session.send(MessageType.PLAY_WAITING, EmptyPayload.INSTANCE);

        matchmakingService.enqueue(new MatchRequest(user.getId(), user.getUsername(), user.getElo(), new MatchListener() {
            @Override
            public void onMatchFound(String roomId, char assignedColor, String opponentUsername, int opponentElo) {
                session.setCurrentRoomId(roomId);
                roomManager.attachSink(roomId, user.getId(), session);
                session.send(MessageType.MATCH_FOUND,
                        new MatchFoundPayload(roomId, assignedColor, opponentUsername, opponentElo));
            }

            @Override
            public void onNotFound() {
                session.send(MessageType.PLAY_NOT_FOUND,
                        new ErrorPayload("Couldn't find an opponent within your ELO range. Try again."));
            }
        }));
    }
}
