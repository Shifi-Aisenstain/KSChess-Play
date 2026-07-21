package shared.protocol.payload;

/** Marker payload for messages that carry no data (PING, PONG, RESIGN, cancellations...). */
public final class EmptyPayload {
    public static final EmptyPayload INSTANCE = new EmptyPayload();
}
