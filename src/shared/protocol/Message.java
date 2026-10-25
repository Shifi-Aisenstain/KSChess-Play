package shared.protocol;

public final class Message {
    private final MessageType type;
    private final String payloadJson;
    private final String traceId;

    public Message(MessageType type, String payloadJson, String traceId) {
        this.type = type;
        this.payloadJson = payloadJson;
        this.traceId = traceId;
    }

    public MessageType getType() { return type; }
    public String getPayloadJson() { return payloadJson; }
    public String getTraceId() { return traceId; }
}
