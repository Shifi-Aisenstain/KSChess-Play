package shared.tracing;

import java.util.LinkedHashMap;
import java.util.Map;

public final class Span {
    public final String traceId;
    public final String spanId;
    public final String name;
    public final long startEpochMicros;
    private final Map<String, String> tags = new LinkedHashMap<>();
    private long durationMicros = -1;

    private Span(String traceId, String spanId, String name, long startEpochMicros) {
        this.traceId = traceId;
        this.spanId = spanId;
        this.name = name;
        this.startEpochMicros = startEpochMicros;
    }

    public static Span start(String traceId, String name) {
        return new Span(traceId != null ? traceId : TraceContext.newId(), TraceContext.newSpanId(), name,
                System.currentTimeMillis() * 1000L);
    }

    public Span tag(String key, String value) {
        tags.put(key, value);
        return this;
    }

    public Map<String, String> tags() {
        return tags;
    }

    public long finish() {
        durationMicros = Math.max(1, System.currentTimeMillis() * 1000L - startEpochMicros);
        return durationMicros;
    }

    public long durationMicros() {
        return durationMicros;
    }
}
