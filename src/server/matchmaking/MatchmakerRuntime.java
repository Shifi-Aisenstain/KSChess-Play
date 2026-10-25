package server.matchmaking;

import server.logging.ServerActivityLogger;
import shared.bus.MessageBus;
import shared.bus.RoomBusSubjects;
import shared.metrics.MetricsRegistry;
import shared.protocol.Message;
import shared.protocol.MessageCodec;
import shared.protocol.MessageType;
import shared.protocol.payload.BusMatchFoundPayload;
import shared.protocol.payload.BusMatchNotFoundPayload;
import shared.protocol.payload.CancelMatchRequestPayload;
import shared.protocol.payload.MatchEnqueueRequestPayload;

public final class MatchmakerRuntime {
    private final MessageBus bus;
    private final MatchmakingService matchmakingService;
    private final ServerActivityLogger activityLogger;
    private final MetricsRegistry metricsRegistry;

    public MatchmakerRuntime(MessageBus bus, MatchmakingService matchmakingService, ServerActivityLogger activityLogger,
                              MetricsRegistry metricsRegistry) {
        this.bus = bus;
        this.matchmakingService = matchmakingService;
        this.activityLogger = activityLogger;
        this.metricsRegistry = metricsRegistry;
    }

    public void start() {
        bus.subscribe(RoomBusSubjects.MATCHMAKING_REQUESTS, this::onMatchmakingRequest);
        bus.subscribe(RoomBusSubjects.MATCHMAKING_CANCEL, this::onMatchmakingCancel);
    }

    private void onMatchmakingRequest(Message message, String replyTo) {
        MatchEnqueueRequestPayload request = MessageCodec.decodePayload(message, MatchEnqueueRequestPayload.class);
        activityLogger.logTraffic("IN", request.userId, "PLAY_REQUEST", "elo=" + request.elo);
        metricsRegistry.incrementCounter("matchmaker_requests_total", "Total matchmaking enqueue requests received");
        matchmakingService.enqueue(new MatchRequest(request.userId, request.username, request.elo, new MatchListener() {
            @Override
            public void onMatchFound(String roomId, char assignedColor, String opponentUsername, int opponentElo) {
                activityLogger.logTraffic("OUT", request.userId, "MATCH_FOUND",
                        "roomId=" + roomId + " opponent=" + opponentUsername);
                metricsRegistry.incrementCounter("matchmaker_matches_found_total", "Total successful matches made");
                bus.publish(RoomBusSubjects.MATCHMAKING_RESULTS, MessageType.MATCH_FOUND,
                        new BusMatchFoundPayload(request.userId, roomId, assignedColor, opponentUsername, opponentElo));
            }

            @Override
            public void onNotFound() {
                activityLogger.logTraffic("OUT", request.userId, "PLAY_NOT_FOUND", "{}");
                metricsRegistry.incrementCounter("matchmaker_not_found_total", "Total matchmaking requests that expired unmatched");
                bus.publish(RoomBusSubjects.MATCHMAKING_RESULTS, MessageType.PLAY_NOT_FOUND,
                        new BusMatchNotFoundPayload(request.userId));
            }
        }));
    }

    private void onMatchmakingCancel(Message message, String replyTo) {
        CancelMatchRequestPayload request = MessageCodec.decodePayload(message, CancelMatchRequestPayload.class);
        activityLogger.logTraffic("IN", request.userId, "PLAY_CANCEL", "{}");
        metricsRegistry.incrementCounter("matchmaker_cancels_total", "Total matchmaking cancel requests");
        matchmakingService.cancel(request.userId);
    }
}
