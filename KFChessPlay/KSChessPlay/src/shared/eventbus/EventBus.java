package shared.eventbus;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A minimal, thread-safe, topic-based publish/subscribe bus (Observer
 * pattern, generalized so publishers and subscribers never reference each
 * other directly).
 *
 * <p>Both the server and the client own their own {@code EventBus}
 * instance:
 * <ul>
 *   <li><b>Server side</b>: {@code engine.GameManager} publishes
 *       {@code MoveExecutedEvent}/{@code GameStartedEvent}/{@code GameEndedEvent}.
 *       {@code ServerActivityLogger} and the score/move-log bookkeeping
 *       subscribe to them; a network listener re-broadcasts a trimmed
 *       version to clients as {@code GAME_EVENT} messages.</li>
 *   <li><b>Client side</b>: incoming {@code GAME_EVENT} messages are
 *       re-published on a local bus so that sound and animation
 *       subscribers stay fully decoupled from the networking code.</li>
 * </ul>
 *
 * This is what the course spec calls "the bus" - one reusable class, two
 * independent instances, four categories of subscribers (scores, move
 * logs, sound, animations).
 */
public final class EventBus {
    private final Map<Class<?>, List<Subscriber<?>>> subscribers = new ConcurrentHashMap<>();

    public <T extends Event> void subscribe(Class<T> eventType, Subscriber<T> subscriber) {
        subscribers.computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>()).add(subscriber);
    }

    public <T extends Event> void unsubscribe(Class<T> eventType, Subscriber<T> subscriber) {
        List<Subscriber<?>> list = subscribers.get(eventType);
        if (list != null) list.remove(subscriber);
    }

    @SuppressWarnings("unchecked")
    public <T extends Event> void publish(T event) {
        List<Subscriber<?>> list = subscribers.get(event.getClass());
        if (list == null) return;
        for (Subscriber<?> subscriber : list) {
            ((Subscriber<T>) subscriber).onEvent(event);
        }
    }
}
