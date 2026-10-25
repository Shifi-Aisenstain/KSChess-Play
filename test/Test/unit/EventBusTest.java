package Test.unit;

import org.junit.jupiter.api.Test;
import shared.eventbus.Event;
import shared.eventbus.EventBus;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class EventBusTest {

    private static final class FakeEventA implements Event {
        final String label;
        FakeEventA(String label) { this.label = label; }
    }

    private static final class FakeEventB implements Event {
    }

    @Test
    public void testSubscriberReceivesPublishedEventOfItsType() {
        EventBus bus = new EventBus();
        List<String> received = new ArrayList<>();
        bus.subscribe(FakeEventA.class, e -> received.add(e.label));

        bus.publish(new FakeEventA("hello"));

        assertEquals(List.of("hello"), received);
    }

    @Test
    public void testSubscriberDoesNotReceiveOtherEventTypes() {
        EventBus bus = new EventBus();
        List<String> received = new ArrayList<>();
        bus.subscribe(FakeEventA.class, e -> received.add(e.label));

        bus.publish(new FakeEventB());

        assertTrue(received.isEmpty());
    }

    @Test
    public void testMultipleSubscribersAllReceiveTheEvent() {
        EventBus bus = new EventBus();
        List<String> a = new ArrayList<>();
        List<String> b = new ArrayList<>();
        bus.subscribe(FakeEventA.class, e -> a.add(e.label));
        bus.subscribe(FakeEventA.class, e -> b.add(e.label));

        bus.publish(new FakeEventA("x"));

        assertEquals(List.of("x"), a);
        assertEquals(List.of("x"), b);
    }

    @Test
    public void testUnsubscribeStopsFurtherDelivery() {
        EventBus bus = new EventBus();
        List<String> received = new ArrayList<>();
        shared.eventbus.Subscriber<FakeEventA> subscriber = e -> received.add(e.label);
        bus.subscribe(FakeEventA.class, subscriber);
        bus.unsubscribe(FakeEventA.class, subscriber);

        bus.publish(new FakeEventA("ignored"));

        assertTrue(received.isEmpty());
    }

    @Test
    public void testPublishWithNoSubscribersDoesNothing() {
        EventBus bus = new EventBus();
        assertDoesNotThrow(() -> bus.publish(new FakeEventA("no listeners")));
    }
}
