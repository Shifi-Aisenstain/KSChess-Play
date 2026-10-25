package Test.unit;

import org.junit.jupiter.api.Test;
import shared.bus.InMemoryMessageBus;
import shared.bus.MessageBus;
import shared.protocol.Message;
import shared.protocol.MessageCodec;
import shared.protocol.MessageType;
import shared.protocol.payload.ErrorPayload;
import shared.tracing.TraceContext;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

public class MessageBusTest {

    @org.junit.jupiter.api.BeforeEach
    public void clearTraceBeforeEachTest() {
        TraceContext.clear();
    }

    @org.junit.jupiter.api.AfterEach
    public void clearTraceAfterEachTest() {
        TraceContext.clear();
    }

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

    @Test
    public void testPublishedMessageCarriesThePublishersCurrentTraceId() {
        MessageBus bus = new InMemoryMessageBus();
        List<String> receivedTraceIds = new ArrayList<>();
        bus.subscribe("room.abc.commands", (msg, replyTo) -> receivedTraceIds.add(msg.getTraceId()));

        TraceContext.set("trace-from-caller");
        bus.publish("room.abc.commands", MessageType.ERROR, new ErrorPayload("hello"));

        assertEquals(List.of("trace-from-caller"), receivedTraceIds);
    }

    @Test
    public void testHandlerSeesThePublishersTraceIdAsItsOwnCurrentTraceWhilePublishing() {
        MessageBus bus = new InMemoryMessageBus();
        List<String> observedInsideHandler = new ArrayList<>();
        bus.subscribe("allocator.rooms", (msg, replyTo) -> observedInsideHandler.add(TraceContext.current()));

        TraceContext.set("trace-xyz");
        bus.publish("allocator.rooms", MessageType.ERROR, new ErrorPayload("go"));

        assertEquals(List.of("trace-xyz"), observedInsideHandler,
                "a handler reacting to a bus message must see that message's traceId as its own current trace, so any further publish it makes propagates the same id onward");
        assertEquals("trace-xyz", TraceContext.current(), "delivering to a handler must not clobber the publisher's own trace context afterward");
    }

    @Test
    public void testPublishGeneratesAFreshTraceIdWhenCallerHasNone() {
        MessageBus bus = new InMemoryMessageBus();
        List<String> receivedTraceIds = new ArrayList<>();
        bus.subscribe("matchmaking.requests", (msg, replyTo) -> receivedTraceIds.add(msg.getTraceId()));

        bus.publish("matchmaking.requests", MessageType.ERROR, new ErrorPayload("hello"));

        assertEquals(1, receivedTraceIds.size());
        assertNotNull(receivedTraceIds.get(0));
    }
}
