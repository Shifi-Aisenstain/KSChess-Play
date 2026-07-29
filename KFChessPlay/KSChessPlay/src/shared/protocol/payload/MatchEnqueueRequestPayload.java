package shared.protocol.payload;

import lombok.AllArgsConstructor;

/** Bus request on {@code matchmaking.requests} (PLAN_split_6_services.md §2/§3.2): WS Gateway ->
 *  monolith (standing in for the not-yet-split Matchmaker, per §3.3), fire-and-forget - mirrors the
 *  original in-process {@code MatchmakingService.enqueue(new MatchRequest(...))} call. */
@AllArgsConstructor
public final class MatchEnqueueRequestPayload {
    public final long userId;
    public final String username;
    public final int elo;
}
