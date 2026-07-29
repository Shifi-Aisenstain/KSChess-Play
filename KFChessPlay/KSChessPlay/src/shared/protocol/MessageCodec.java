package shared.protocol;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Central (de)serialization point. Every class that needs to turn a
 * {@link Message} into JSON - or JSON back into a typed payload - goes
 * through here, so Gson configuration (naming policy, pretty printing,
 * etc.) lives in exactly one place.
 */
public final class MessageCodec {
    private static final Gson GSON = new GsonBuilder().create();

    private MessageCodec() { }

    public static String encode(MessageType type, Object payload) {
        Message message = new Message(type, GSON.toJson(payload));
        return GSON.toJson(message);
    }

    public static Message decodeEnvelope(String json) {
        return GSON.fromJson(json, Message.class);
    }

    public static <T> T decodePayload(Message message, Class<T> payloadClass) {
        return GSON.fromJson(message.getPayloadJson(), payloadClass);
    }

    /**
     * Encodes a payload to JSON on its own, without wrapping it in a {@link Message} envelope.
     * Used by the internal bus routing payloads ({@code RoutedCommandPayload}/{@code RoutedUpdatePayload},
     * see PLAN_split_6_services.md §3.1) to carry an arbitrary inner payload as a nested JSON string
     * alongside routing metadata (target userId, inner {@link MessageType}) - decode the nested
     * string back with {@link #decodeInnerPayload}.
     */
    public static String encodeInnerPayload(Object payload) {
        return GSON.toJson(payload);
    }

    public static <T> T decodeInnerPayload(String json, Class<T> payloadClass) {
        return GSON.fromJson(json, payloadClass);
    }
}
