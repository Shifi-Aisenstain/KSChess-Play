package shared.protocol.payload;

import lombok.AllArgsConstructor;

/** Bus request on {@code matchmaking.cancel} (PLAN_split_6_services.md §3.2): WS Gateway ->
 *  monolith, fire-and-forget - mirrors the original in-process {@code MatchmakingService.cancel(userId)}. */
@AllArgsConstructor
public final class CancelMatchRequestPayload {
    public final long userId;
}
