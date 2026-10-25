package shared.protocol.payload;

import lombok.AllArgsConstructor;
import shared.protocol.MessageType;

@AllArgsConstructor
public final class RoutedUpdatePayload {
    public final Long targetUserId;
    public final MessageType innerType;
    public final String innerPayloadJson;
}
