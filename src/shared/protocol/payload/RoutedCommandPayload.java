package shared.protocol.payload;

import lombok.AllArgsConstructor;
import shared.protocol.MessageType;

@AllArgsConstructor
public final class RoutedCommandPayload {
    public final long userId;
    public final MessageType innerType;
    public final String innerPayloadJson;
}
