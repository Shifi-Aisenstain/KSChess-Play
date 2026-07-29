package shared.bus;

import shared.protocol.Message;
import shared.protocol.MessageCodec;
import shared.protocol.MessageType;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-process stand-in for {@link MessageBus} - delivers synchronously, on the publishing thread,
 * to every subscriber of the exact subject. Used in tests (and anywhere a real NATS server isn't
 * available) so bus-dependent service code can be exercised without spinning up infrastructure.
 *
 * <p>Only exact subject matches are delivered - unlike real NATS this does <b>not</b> support
 * wildcard subjects ({@code *}/{@code >}). That's fine for how the split services use the bus
 * (each subscribes to a concrete, already-known subject such as {@code room.abc123.commands},
 * never a wildcard - see PLAN_split_6_services.md §2), but is a real limitation if a future subject
 * design relies on wildcard subscriptions.
 */
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
            // Synchronous delivery above means any reply() call already ran by the time we get here.
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
        for (BusMessageHandler handler : handlers) {
            handler.onMessage(decoded, replySubject);
        }
    }
}
