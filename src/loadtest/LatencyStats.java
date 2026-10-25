package loadtest;

import java.util.Arrays;
import java.util.List;

public final class LatencyStats {
    public final int sampleCount;
    public final long minMs;
    public final long avgMs;
    public final long p95Ms;
    public final long maxMs;

    private LatencyStats(int sampleCount, long minMs, long avgMs, long p95Ms, long maxMs) {
        this.sampleCount = sampleCount;
        this.minMs = minMs;
        this.avgMs = avgMs;
        this.p95Ms = p95Ms;
        this.maxMs = maxMs;
    }

    public static LatencyStats of(List<Long> samplesMs) {
        if (samplesMs.isEmpty()) {
            return new LatencyStats(0, 0, 0, 0, 0);
        }
        long[] sorted = samplesMs.stream().mapToLong(Long::longValue).sorted().toArray();
        long sum = Arrays.stream(sorted).sum();
        int p95Index = Math.min(sorted.length - 1, (int) Math.ceil(sorted.length * 0.95) - 1);
        return new LatencyStats(sorted.length, sorted[0], sum / sorted.length, sorted[Math.max(p95Index, 0)], sorted[sorted.length - 1]);
    }

    @Override
    public String toString() {
        if (sampleCount == 0) return "no samples";
        return "n=" + sampleCount + " min=" + minMs + "ms avg=" + avgMs + "ms p95=" + p95Ms + "ms max=" + maxMs + "ms";
    }
}
