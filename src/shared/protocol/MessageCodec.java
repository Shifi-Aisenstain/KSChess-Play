package shared.protocol;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import shared.tracing.TraceContext;

public final class MessageCodec {
    private static final Gson GSON = new GsonBuilder().create();

    private MessageCodec() { }

    public static String encode(MessageType type, Object payload) {
        Message message = new Message(type, GSON.toJson(payload), TraceContext.currentOrNew());
        return GSON.toJson(message);
    }

    public static Message decodeEnvelope(String json) {
        return GSON.fromJson(json, Message.class);
    }

    public static <T> T decodePayload(Message message, Class<T> payloadClass) {
        return GSON.fromJson(message.getPayloadJson(), payloadClass);
    }

    public static String encodeInnerPayload(Object payload) {
        return GSON.toJson(payload);
    }

    public static <T> T decodeInnerPayload(String json, Class<T> payloadClass) {
        return GSON.fromJson(json, payloadClass);
    }
}
