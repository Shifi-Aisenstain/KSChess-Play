package shared.protocol;

/**
 * The single envelope that travels over the wire in both directions.
 * {@code payloadJson} is kept as a raw JSON string (rather than a generic
 * {@code <T>} field) so that {@link MessageCodec} can decode the envelope
 * without needing to know the payload's concrete type up front - the
 * {@link MessageType} tells the {@code MessageDispatcher} which payload
 * class to decode it into next. This is the classic "envelope/payload"
 * variant of the Command pattern used by most WebSocket protocols.
 */
public final class Message {
    private final MessageType type;
    private final String payloadJson;

    public Message(MessageType type, String payloadJson) {
        this.type = type;
        this.payloadJson = payloadJson;
    }

    public MessageType getType() { return type; }
    public String getPayloadJson() { return payloadJson; }
}
