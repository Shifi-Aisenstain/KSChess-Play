package shared.protocol.payload;

import lombok.AllArgsConstructor;

/** Published on {@code matchmaking.results} (PLAN_split_6_services.md §2/§3.2): monolith -> every
 *  WS Gateway instance (broadcast - each instance ignores userIds it has no local connection for,
 *  same known multi-instance-routing limitation already called out for STATE_UPDATE in §3.1/§5).
 *  Same fields as the client-facing {@link MatchFoundPayload}, plus {@code userId} for routing. */
@AllArgsConstructor
public final class BusMatchFoundPayload {
    public final long userId;
    public final String roomId;
    public final char assignedColor;
    public final String opponentUsername;
    public final int opponentElo;
}
