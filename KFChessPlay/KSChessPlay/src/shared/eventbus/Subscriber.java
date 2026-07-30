package shared.eventbus;

@FunctionalInterface
public interface Subscriber<T extends Event> {
    void onEvent(T event);
}
