package server.matchmaking;

import shared.bus.MessageBus;
import shared.bus.RoomBusSubjects;
import shared.protocol.Message;
import shared.protocol.MessageCodec;
import shared.protocol.MessageType;
import shared.protocol.payload.BusMatchFoundPayload;
import shared.protocol.payload.BusMatchNotFoundPayload;
import shared.protocol.payload.CancelMatchRequestPayload;
import shared.protocol.payload.MatchEnqueueRequestPayload;

/**
 * The actual "run the matchmaking queue" logic of the Matchmaker (PLAN_split_6_services.md §3.3),
 * factored out of {@link MatchmakerMain} so it can be exercised directly against a
 * {@code shared.bus.InMemoryMessageBus} in tests, the same split as {@code server.shard.GameShardRuntime}
 * vs. {@code GameServerShardMain}. Answers exactly the two matchmaking subjects
 * {@code server.gateway.MatchmakingClient} calls - moved here verbatim from the now-retired
 * {@code server.rpc.MonolithBusGateway}, which stood in for this process before the real split.
 */
public final class MatchmakerRuntime {
    private final MessageBus bus;
    private final MatchmakingService matchmakingService;

    public MatchmakerRuntime(MessageBus bus, MatchmakingService matchmakingService) {
        this.bus = bus;
        this.matchmakingService = matchmakingService;
    }

    public void start() {
        bus.subscribe(RoomBusSubjects.MATCHMAKING_REQUESTS, this::onMatchmakingRequest);
        bus.subscribe(RoomBusSubjects.MATCHMAKING_CANCEL, this::onMatchmakingCancel);
    }

    private void onMatchmakingRequest(Message message, String replyTo) {
        MatchEnqueueRequestPayload request = MessageCodec.decodePayload(message, MatchEnqueueRequestPayload.class);
        matchmakingService.enqueue(new MatchRequest(request.userId, request.username, request.elo, new MatchListener() {
            @Override
            public void onMatchFound(String roomId, char assignedColor, String opponentUsername, int opponentElo) {
                bus.publish(RoomBusSubjects.MATCHMAKING_RESULTS, MessageType.MATCH_FOUND,
                        new BusMatchFoundPayload(request.userId, roomId, assignedColor, opponentUsername, opponentElo));
            }

            @Override
            public void onNotFound() {
                bus.publish(RoomBusSubjects.MATCHMAKING_RESULTS, MessageType.PLAY_NOT_FOUND,
                        new BusMatchNotFoundPayload(request.userId));
            }
        }));
    }

    private void onMatchmakingCancel(Message message, String replyTo) {
        CancelMatchRequestPayload request = MessageCodec.decodePayload(message, CancelMatchRequestPayload.class);
        matchmakingService.cancel(request.userId);
    }
}
