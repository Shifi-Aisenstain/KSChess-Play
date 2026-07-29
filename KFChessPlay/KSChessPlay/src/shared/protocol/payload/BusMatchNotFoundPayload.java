package shared.protocol.payload;

import lombok.AllArgsConstructor;

/** Published on {@code matchmaking.results} (PLAN_split_6_services.md §2/§3.2) when a waiting
 *  request times out - see {@link BusMatchFoundPayload}'s javadoc for the routing rationale. */
@AllArgsConstructor
public final class BusMatchNotFoundPayload {
    public final long userId;
}
