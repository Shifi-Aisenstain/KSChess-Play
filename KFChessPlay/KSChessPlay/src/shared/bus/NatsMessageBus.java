package shared.bus;

import io.nats.client.Connection;
import io.nats.client.Dispatcher;
import io.nats.client.Nats;
import shared.protocol.Message;
import shared.protocol.MessageCodec;
import shared.protocol.MessageType;
import shared.tracing.TraceContext;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;

public final class NatsMessageBus implements MessageBus {
    private final Connection connection;

    private NatsMessageBus(Connection connection) {
        this.connection = connection;
    }

    public static NatsMessageBus connect(String natsUrl) {
        try {
            return new NatsMessageBus(Nats.connect(natsUrl));
        } catch (Exception e) {
            throw new IllegalStateException("Could not connect to NATS at " + natsUrl, e);
        }
    }

    @Override
    public void publish(String subject, MessageType type, Object payload) {
        connection.publish(subject, toBytes(type, payload));
    }

    @Override
    public Subscription subscribe(String subject, BusMessageHandler handler) {
        Dispatcher dispatcher = connection.createDispatcher(natsMessage -> {
            Message decoded = MessageCodec.decodeEnvelope(new String(natsMessage.getData(), StandardCharsets.UTF_8));
            TraceContext.runWithTrace(decoded.getTraceId(), () -> handler.onMessage(decoded, natsMessage.getReplyTo()));
        });
        dispatcher.subscribe(subject);
        return () -> connection.closeDispatcher(dispatcher);
    }

    @Override
    public Optional<Message> request(String subject, MessageType type, Object payload, Duration timeout) {
        try {
            io.nats.client.Message reply = connection.request(subject, toBytes(type, payload), timeout);
            if (reply == null) return Optional.empty();
            return Optional.of(MessageCodec.decodeEnvelope(new String(reply.getData(), StandardCharsets.UTF_8)));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
    }

    @Override
    public void reply(String replySubject, MessageType type, Object payload) {
        connection.publish(replySubject, toBytes(type, payload));
    }

    @Override
    public void close() {
        try {
            connection.close();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static byte[] toBytes(MessageType type, Object payload) {
        return MessageCodec.encode(type, payload).getBytes(StandardCharsets.UTF_8);
    }
}
