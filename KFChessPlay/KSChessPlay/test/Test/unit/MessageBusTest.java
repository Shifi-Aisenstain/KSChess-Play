package Test.unit;

import org.junit.jupiter.api.Test;
import shared.bus.InMemoryMessageBus;
import shared.bus.MessageBus;
import shared.protocol.Message;
import shared.protocol.MessageCodec;
import shared.protocol.MessageType;
import shared.protocol.payload.ErrorPayload;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Exercises the {@link MessageBus} contract (publish/subscribe, request/reply, unsubscribe)
 * against {@link InMemoryMessageBus}, since the real {@link shared.bus.NatsMessageBus} needs a
 * live NATS server (see PLAN_split_6_services.md, sub-phase 3.0) - both implement the same
 * interface, so this is what actually gets exercised in `mvn test`.
 */
public class MessageBusTest {

    @Test
    public void testSubscriberReceivesPublishedMessage() {
        MessageBus bus = new InMemoryMessageBus();
        List<String> received = new ArrayList<>();
        bus.subscribe("room.abc.commands", (msg, replyTo) ->
                received.add(MessageCodec.decodePayload(msg, ErrorPayload.class).message));

        bus.publish("room.abc.commands", MessageType.ERROR, new ErrorPayload("hello"));

        assertEquals(List.of("hello"), received);
    }

    @Test
    public void testSubscriberOnDifferentSubjectDoesNotReceive() {
        MessageBus bus = new InMemoryMessageBus();
        List<String> received = new ArrayList<>();
        bus.subscribe("room.abc.commands", (msg, replyTo) -> received.add("got it"));

        bus.publish("room.other.commands", MessageType.ERROR, new ErrorPayload("hello"));

        assertTrue(received.isEmpty());
    }

    @Test
    public void testUnsubscribeStopsFurtherDelivery() {
        MessageBus bus = new InMemoryMessageBus();
        List<String> received = new ArrayList<>();
        MessageBus.Subscription subscription = bus.subscribe("matchmaking.requests",
                (msg, replyTo) -> received.add("got it"));

        subscription.unsubscribe();
        bus.publish("matchmaking.requests", MessageType.ERROR, new ErrorPayload("ignored"));

        assertTrue(received.isEmpty());
    }

    @Test
    public void testRequestReplyRoundTrip() {
        MessageBus bus = new InMemoryMessageBus();
        // Simulates a service (e.g. Game Allocator) answering a request from another service
        // (e.g. WS Gateway asking "which room is this user in?").
        bus.subscribe("user.42.active-room", (msg, replyTo) ->
                bus.reply(replyTo, MessageType.ERROR, new ErrorPayload("room-xyz")));

        Optional<Message> reply = bus.request("user.42.active-room", MessageType.ERROR,
                new ErrorPayload("who?"), Duration.ofSeconds(1));

        assertTrue(reply.isPresent());
        assertEquals("room-xyz", MessageCodec.decodePayload(reply.get(), ErrorPayload.class).message);
    }

    @Test
    public void testRequestWithNoSubscriberReturnsEmpty() {
        MessageBus bus = new InMemoryMessageBus();

        Optional<Message> reply = bus.request("nobody.listening", MessageType.ERROR,
                new ErrorPayload("hello?"), Duration.ofMillis(50));

        assertTrue(reply.isEmpty());
    }

    @Test
    public void testMultipleSubscribersOnSameSubjectAllReceive() {
        MessageBus bus = new InMemoryMessageBus();
        List<String> a = new ArrayList<>();
        List<String> b = new ArrayList<>();
        bus.subscribe("room.allocated", (msg, replyTo) -> a.add("a"));
        bus.subscribe("room.allocated", (msg, replyTo) -> b.add("b"));

        bus.publish("room.allocated", MessageType.ERROR, new ErrorPayload("go"));

        assertEquals(List.of("a"), a);
        assertEquals(List.of("b"), b);
    }
}
