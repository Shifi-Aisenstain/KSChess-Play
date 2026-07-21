package shared.eventbus;

/** A subscriber reacts to exactly one event type. Plain functional interface -> lambdas welcome. */
@FunctionalInterface
public interface Subscriber<T extends Event> {
    void onEvent(T event);
}
