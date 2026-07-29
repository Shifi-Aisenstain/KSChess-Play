package shared.protocol.payload;

import lombok.AllArgsConstructor;

/** Bus request on {@code room.join} (PLAN_split_6_services.md §3.2): WS Gateway -> monolith. See
 *  {@link CreateRoomRequestPayload}'s javadoc for the same rationale. */
@AllArgsConstructor
public final class JoinRoomRequestPayload {
    public final long userId;
    public final String username;
    public final int elo;
    public final String roomId;
}
