package shared.tracing;

import java.util.concurrent.ThreadLocalRandom;

public final class TraceContext {
    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    private TraceContext() { }

    public static String newId() {
        return String.format("%016x", ThreadLocalRandom.current().nextLong())
                + String.format("%016x", ThreadLocalRandom.current().nextLong());
    }

    public static String newSpanId() {
        return String.format("%016x", ThreadLocalRandom.current().nextLong());
    }

    public static String current() {
        return CURRENT.get();
    }

    public static String currentOrNew() {
        String existing = CURRENT.get();
        if (existing != null) {
            return existing;
        }
        String generated = newId();
        CURRENT.set(generated);
        return generated;
    }

    public static void runWithTrace(String traceId, Runnable action) {
        String previous = CURRENT.get();
        CURRENT.set(traceId != null ? traceId : newId());
        try {
            action.run();
        } finally {
            if (previous != null) {
                CURRENT.set(previous);
            } else {
                CURRENT.remove();
            }
        }
    }

    public static void set(String traceId) {
        CURRENT.set(traceId != null ? traceId : newId());
    }

    public static void clear() {
        CURRENT.remove();
    }
}
