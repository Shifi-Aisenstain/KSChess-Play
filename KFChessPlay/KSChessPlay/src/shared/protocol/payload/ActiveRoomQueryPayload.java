package shared.protocol.payload;

import lombok.AllArgsConstructor;

/** Request on {@code RoomBusSubjects#ACTIVE_ROOM_LOOKUP} (PLAN_split_6_services.md §3.2): WS Gateway
 *  -> monolith. See {@link ActiveRoomReplyPayload}'s javadoc / {@code RoomBusSubjects} for why this
 *  is one shared subject with the userId in the payload rather than a per-user subject. */
@AllArgsConstructor
public final class ActiveRoomQueryPayload {
    public final long userId;
}
