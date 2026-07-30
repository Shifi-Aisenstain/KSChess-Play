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

/**
 * WS Gateway's bus-facing client for matchmaking (PLAN_split_6_services.md §3.2, standing in for the
 * not-yet-split Matchmaker - real split is §3.3). Replaces the direct in-process
 * {@code MatchmakingService.enqueue}/{@code .cancel} calls {@code PlayRequestHandler}/
 * {@code PlayCancelHandler} used to make: enqueueing is now fire-and-forget over
 * {@code matchmaking.requests}, and the eventual result comes back asynchronously on the shared,
 * broadcast {@code matchmaking.results} subject (one subscription per Gateway process, not one per
 * request - see {@link RoomBusSubjects#MATCHMAKING_RESULTS}'s javadoc), so this class keeps a local
 * userId -> {@link MatchListener} table to know which locally-waiting caller each result belongs to.
 */
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
        // Any other type on this subject would be a bug elsewhere on the bus - silently ignored here,
        // same "ignore what isn't ours" policy as GatewayRoomSession's onUpdate for foreign userIds.
    }
}
