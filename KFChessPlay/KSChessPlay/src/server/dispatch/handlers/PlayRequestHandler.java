package server.dispatch.handlers;

import server.auth.User;
import server.dispatch.MessageHandler;
import server.gateway.GatewayRoomRegistry;
import server.gateway.MatchmakingClient;
import server.matchmaking.MatchListener;
import server.net.PlayerSession;
import shared.protocol.Message;
import shared.protocol.MessageType;
import shared.protocol.payload.EmptyPayload;
import shared.protocol.payload.ErrorPayload;
import shared.protocol.payload.MatchFoundPayload;

public final class PlayRequestHandler implements MessageHandler {
    private final MatchmakingClient matchmakingClient;
    private final GatewayRoomRegistry roomRegistry;

    public PlayRequestHandler(MatchmakingClient matchmakingClient, GatewayRoomRegistry roomRegistry) {
        this.matchmakingClient = matchmakingClient;
        this.roomRegistry = roomRegistry;
    }

    @Override
    public void handle(PlayerSession session, Message message) {
        User user = HandlerSupport.requireUser(session);
        if (user == null) return;

        session.send(MessageType.PLAY_WAITING, EmptyPayload.INSTANCE);

        matchmakingClient.enqueue(user.getId(), user.getUsername(), user.getElo(), new MatchListener() {
            @Override
            public void onMatchFound(String roomId, char assignedColor, String opponentUsername, int opponentElo) {
                session.setCurrentRoomId(roomId);
                // Matchmaking always allocates a Shard immediately for both players (see
                // RoomManager.createRoomForMatch) - by the time this callback fires, room.allocated
                // has already been published (see GatewayRoomRegistry's javadoc for the exact
                // ordering guarantee this relies on), so this always attaches immediately.
                roomRegistry.registerPendingMember(roomId, user.getId(), session);
                session.send(MessageType.MATCH_FOUND,
                        new MatchFoundPayload(roomId, assignedColor, opponentUsername, opponentElo));
            }

            @Override
            public void onNotFound() {
                session.send(MessageType.PLAY_NOT_FOUND,
                        new ErrorPayload("Couldn't find an opponent within your ELO range. Try again."));
            }
        });
    }
}
