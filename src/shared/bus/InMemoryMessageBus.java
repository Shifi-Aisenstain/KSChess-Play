package shared.bus;

import shared.protocol.Message;
import shared.protocol.MessageCodec;
import shared.protocol.MessageType;
import shared.tracing.TraceContext;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

public final class InMemoryMessageBus implements MessageBus {
    private final Map<String, List<BusMessageHandler>> subscribers = new ConcurrentHashMap<>();
    private final AtomicLong replySubjectCounter = new AtomicLong();

    @Override
    public void publish(String subject, MessageType type, Object payload) {
        deliver(subject, MessageCodec.encode(type, payload), null);
    }

    @Override
    public Subscription subscribe(String subject, BusMessageHandler handler) {
        List<BusMessageHandler> handlers = subscribers.computeIfAbsent(subject, s -> new CopyOnWriteArrayList<>());
        handlers.add(handler);
        return () -> handlers.remove(handler);
    }

    @Override
    public Optional<Message> request(String subject, MessageType type, Object payload, Duration timeout) {
        String replySubject = "_INPROC_REPLY." + replySubjectCounter.incrementAndGet();
        Message[] holder = new Message[1];
        Subscription replySubscription = subscribe(replySubject, (msg, ignoredReplyTo) -> holder[0] = msg);
        try {
            deliver(subject, MessageCodec.encode(type, payload), replySubject);

            return Optional.ofNullable(holder[0]);
        } finally {
            replySubscription.unsubscribe();
        }
    }

    @Override
    public void reply(String replySubject, MessageType type, Object payload) {
        deliver(replySubject, MessageCodec.encode(type, payload), null);
    }

    @Override
    public void close() {
        subscribers.clear();
    }

    private void deliver(String subject, String envelopeJson, String replySubject) {
        List<BusMessageHandler> handlers = subscribers.get(subject);
        if (handlers == null) return;
        Message decoded = MessageCodec.decodeEnvelope(envelopeJson);
        TraceContext.runWithTrace(decoded.getTraceId(), () -> {
            for (BusMessageHandler handler : handlers) {
                handler.onMessage(decoded, replySubject);
            }
        });
    }
}
