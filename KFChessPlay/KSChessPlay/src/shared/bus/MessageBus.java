package shared.bus;

import shared.protocol.Message;
import shared.protocol.MessageCodec;
import shared.protocol.MessageType;

import java.time.Duration;
import java.util.Optional;

/**
 * Thin abstraction over the internal service-to-service bus introduced for the 6-way split
 * (Server_Design.md §3.3: WS Gateway &lt;-&gt; Game Server Shards, Matchmaker &lt;-&gt; Game
 * Allocator, WS Gateway &lt;-&gt; Game Allocator for room lifecycle). Every service that talks
 * over the bus depends on this interface, not on the NATS client directly - {@link NatsMessageBus}
 * is the real (NATS-backed) implementation used in production/docker-compose, and
 * {@link InMemoryMessageBus} is an in-process test double used in unit tests so bus-dependent
 * code can be exercised without a live NATS server.
 *
 * <p>Reuses the exact envelope/payload contract already proven for the client-facing WebSocket
 * protocol ({@link MessageCodec}/{@link Message}/{@link MessageType}) instead of inventing a
 * second serialization format for internal traffic.
 */
public interface MessageBus extends AutoCloseable {

    /** Fire-and-forget publish: encodes {@code type}/{@code payload} with {@link MessageCodec}
     *  and publishes the resulting envelope to {@code subject}. */
    void publish(String subject, MessageType type, Object payload);

    /** Registers {@code handler} to receive every message published to {@code subject}.
     *  Returns a {@link Subscription} the caller can use to stop listening. */
    Subscription subscribe(String subject, BusMessageHandler handler);

    /** Request/reply: publishes to {@code subject} and waits up to {@code timeout} for a reply
     *  sent via {@link #reply}. Empty if no reply arrived in time. */
    Optional<Message> request(String subject, MessageType type, Object payload, Duration timeout);

    /** Sends {@code type}/{@code payload} back to whoever is waiting on {@code replySubject} -
     *  called from inside a {@link BusMessageHandler} that's answering a {@link #request} call
     *  (see {@link BusMessageHandler#onMessage}'s {@code replySubject} parameter). */
    void reply(String replySubject, MessageType type, Object payload);

    @Override
    void close();

    /** Handle returned by {@link #subscribe} so callers can stop listening later. */
    interface Subscription {
        void unsubscribe();
    }
}
