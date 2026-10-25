package Test.unit;

import loadtest.LatencyStats;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class LatencyStatsTest {

    @Test
    public void testEmptySamplesProduceZeroedStats() {
        LatencyStats stats = LatencyStats.of(List.of());

        assertEquals(0, stats.sampleCount);
        assertEquals(0, stats.minMs);
        assertEquals(0, stats.avgMs);
        assertEquals(0, stats.p95Ms);
        assertEquals(0, stats.maxMs);
        assertEquals("no samples", stats.toString());
    }

    @Test
    public void testSingleSampleIsMinAvgP95AndMax() {
        LatencyStats stats = LatencyStats.of(List.of(42L));

        assertEquals(1, stats.sampleCount);
        assertEquals(42, stats.minMs);
        assertEquals(42, stats.avgMs);
        assertEquals(42, stats.p95Ms);
        assertEquals(42, stats.maxMs);
    }

    @Test
    public void testMultipleSamplesComputeMinAvgMax() {
        LatencyStats stats = LatencyStats.of(List.of(10L, 20L, 30L, 40L, 50L));

        assertEquals(5, stats.sampleCount);
        assertEquals(10, stats.minMs);
        assertEquals(30, stats.avgMs);
        assertEquals(50, stats.maxMs);
    }

    @Test
    public void testP95IsNearTheTopOfALargeSortedSample() {
        List<Long> samples = java.util.stream.LongStream.rangeClosed(1, 100).boxed().toList();

        LatencyStats stats = LatencyStats.of(samples);

        assertEquals(1, stats.minMs);
        assertEquals(100, stats.maxMs);
        assertTrue(stats.p95Ms >= 94 && stats.p95Ms <= 96, "p95 of 1..100 should land right around 95, was " + stats.p95Ms);
    }

    @Test
    public void testToStringIncludesAllFieldsWhenSamplesExist() {
        LatencyStats stats = LatencyStats.of(List.of(5L, 15L));

        String rendered = stats.toString();
        assertTrue(rendered.contains("n=2"));
        assertTrue(rendered.contains("min=5ms"));
        assertTrue(rendered.contains("max=15ms"));
    }
}
