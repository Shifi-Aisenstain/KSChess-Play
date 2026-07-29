package shared.protocol.payload;

import lombok.AllArgsConstructor;

/** Reply on {@code user.<userId>.active-room} (PLAN_split_6_services.md §2/§3.2): monolith -> WS
 *  Gateway. {@code roomId} is null when the user has no in-progress game - always replying (rather
 *  than leaving the requester to time out for "no room", the way a bare bus request with no
 *  responder would) keeps the common "not reconnecting mid-game" case fast. */
@AllArgsConstructor
public final class ActiveRoomReplyPayload {
    public final String roomId;
}
