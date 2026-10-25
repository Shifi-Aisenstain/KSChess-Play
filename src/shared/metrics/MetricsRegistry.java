package shared.metrics;

import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

public final class MetricsRegistry {
    private final Map<String, LongAdder> counters = new ConcurrentHashMap<>();
    private final Map<String, String> counterHelp = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> gauges = new ConcurrentHashMap<>();
    private final Map<String, String> gaugeHelp = new ConcurrentHashMap<>();

    public void incrementCounter(String name, String help) {
        incrementCounterBy(name, help, 1);
    }

    public void incrementCounterBy(String name, String help, long amount) {
        counterHelp.putIfAbsent(name, help);
        counters.computeIfAbsent(name, n -> new LongAdder()).add(amount);
    }

    public void setGauge(String name, String help, long value) {
        gaugeHelp.putIfAbsent(name, help);
        gauges.computeIfAbsent(name, n -> new AtomicLong()).set(value);
    }

    public void incrementGauge(String name, String help) {
        gaugeHelp.putIfAbsent(name, help);
        gauges.computeIfAbsent(name, n -> new AtomicLong()).incrementAndGet();
    }

    public void decrementGauge(String name, String help) {
        gaugeHelp.putIfAbsent(name, help);
        gauges.computeIfAbsent(name, n -> new AtomicLong()).decrementAndGet();
    }

    public String renderPrometheusText() {
        StringBuilder body = new StringBuilder();
        for (Map.Entry<String, Long> entry : sortedCounters().entrySet()) {
            String name = entry.getKey();
            body.append("# HELP ").append(name).append(' ').append(counterHelp.getOrDefault(name, "")).append('\n');
            body.append("# TYPE ").append(name).append(" counter\n");
            body.append(name).append(' ').append(entry.getValue()).append('\n');
        }
        for (Map.Entry<String, Long> entry : sortedGauges().entrySet()) {
            String name = entry.getKey();
            body.append("# HELP ").append(name).append(' ').append(gaugeHelp.getOrDefault(name, "")).append('\n');
            body.append("# TYPE ").append(name).append(" gauge\n");
            body.append(name).append(' ').append(entry.getValue()).append('\n');
        }
        return body.toString();
    }

    private Map<String, Long> sortedCounters() {
        Map<String, Long> result = new TreeMap<>();
        counters.forEach((name, adder) -> result.put(name, adder.sum()));
        return result;
    }

    private Map<String, Long> sortedGauges() {
        Map<String, Long> result = new TreeMap<>();
        gauges.forEach((name, value) -> result.put(name, value.get()));
        return result;
    }
}
