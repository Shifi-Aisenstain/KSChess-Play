package shared.protocol.payload;

import lombok.AllArgsConstructor;
import shared.protocol.MessageType;

/**
 * Envelope payload published on {@code room.<roomId>.updates} (PLAN_split_6_services.md §2/§3.1),
 * under the outer {@code MessageType.ROOM_UPDATE}. {@code targetUserId} is {@code null} for
 * broadcast-to-all traffic (GAME_OVER, GAME_EVENT, DISCONNECT_COUNTDOWN) and non-null for the
 * per-member personalized STATE_UPDATE that used to go out via {@code Room.sendTo} directly.
 */
@AllArgsConstructor
public final class RoutedUpdatePayload {
    public final Long targetUserId;
    public final MessageType innerType;
    public final String innerPayloadJson;
}
