package shared.protocol.payload;

import lombok.AllArgsConstructor;
import shared.protocol.MessageType;

/**
 * Envelope payload published on {@code room.<roomId>.commands} (PLAN_split_6_services.md §2/§3.1),
 * under the outer {@code MessageType.ROOM_COMMAND}. The client-facing command types (SELECT/MOVE/
 * JUMP/RESIGN) don't carry a userId - the server always knew it from the socket session - but a bus
 * command has no socket to fall back on, so it's carried explicitly here alongside the actual
 * command payload (itself encoded separately via {@code MessageCodec#encodeInnerPayload}, since the
 * concrete shape depends on {@link #innerType}).
 */
@AllArgsConstructor
public final class RoutedCommandPayload {
    public final long userId;
    public final MessageType innerType;
    public final String innerPayloadJson;
}
