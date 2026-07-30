package shared.eventbus;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

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
