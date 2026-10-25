package server.gateway;

import server.matchmaking.MatchListener;
import shared.bus.MessageBus;
import shared.bus.RoomBusSubjects;
import shared.protocol.Message;
import shared.protocol.MessageCodec;
import shared.protocol.MessageType;
import shared.protocol.payload.BusMatchFoundPayload;
import shared.protocol.payload.BusMatchNotFoundPayload;
import shared.protocol.payload.CancelMatchRequestPayload;
import shared.protocol.payload.MatchEnqueueRequestPayload;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class MatchmakingClient {
    private final MessageBus bus;
    private final Map<Long, MatchListener> waitingByUserId = new ConcurrentHashMap<>();

    public MatchmakingClient(MessageBus bus) {
        this.bus = bus;
        bus.subscribe(RoomBusSubjects.MATCHMAKING_RESULTS, this::onResult);
    }

    public void enqueue(long userId, String username, int elo, MatchListener listener) {
        waitingByUserId.put(userId, listener);
        bus.publish(RoomBusSubjects.MATCHMAKING_REQUESTS, MessageType.PLAY_REQUEST,
                new MatchEnqueueRequestPayload(userId, username, elo));
    }

    public void cancel(long userId) {
        waitingByUserId.remove(userId);
        bus.publish(RoomBusSubjects.MATCHMAKING_CANCEL, MessageType.PLAY_CANCEL, new CancelMatchRequestPayload(userId));
    }

    private void onResult(Message message, String replyTo) {
        if (message.getType() == MessageType.MATCH_FOUND) {
            BusMatchFoundPayload payload = MessageCodec.decodePayload(message, BusMatchFoundPayload.class);
            MatchListener listener = waitingByUserId.remove(payload.userId);
            if (listener != null) {
                listener.onMatchFound(payload.roomId, payload.assignedColor, payload.opponentUsername, payload.opponentElo);
            }
        } else if (message.getType() == MessageType.PLAY_NOT_FOUND) {
            BusMatchNotFoundPayload payload = MessageCodec.decodePayload(message, BusMatchNotFoundPayload.class);
            MatchListener listener = waitingByUserId.remove(payload.userId);
            if (listener != null) listener.onNotFound();
        }

    }
}
