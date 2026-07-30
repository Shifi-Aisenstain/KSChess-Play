package Test.unit;

import org.junit.jupiter.api.Test;
import shared.metrics.MetricsRegistry;

import static org.junit.jupiter.api.Assertions.*;

public class MetricsRegistryTest {

    @Test
    public void testIncrementCounterAccumulates() {
        MetricsRegistry registry = new MetricsRegistry();

        registry.incrementCounter("requests_total", "Total requests");
        registry.incrementCounter("requests_total", "Total requests");
        registry.incrementCounter("requests_total", "Total requests");

        String rendered = registry.renderPrometheusText();
        assertTrue(rendered.contains("requests_total 3"));
        assertTrue(rendered.contains("# HELP requests_total Total requests"));
        assertTrue(rendered.contains("# TYPE requests_total counter"));
    }

    @Test
    public void testIncrementCounterByAddsGivenAmount() {
        MetricsRegistry registry = new MetricsRegistry();

        registry.incrementCounterBy("updates_total", "Total updates", 5);
        registry.incrementCounterBy("updates_total", "Total updates", 3);

        assertTrue(registry.renderPrometheusText().contains("updates_total 8"));
    }

    @Test
    public void testSetGaugeOverwritesPreviousValue() {
        MetricsRegistry registry = new MetricsRegistry();

        registry.setGauge("active_games", "Active games", 4);
        registry.setGauge("active_games", "Active games", 7);

        String rendered = registry.renderPrometheusText();
        assertTrue(rendered.contains("active_games 7"));
        assertFalse(rendered.contains("active_games 4"));
        assertTrue(rendered.contains("# TYPE active_games gauge"));
    }

    @Test
    public void testIncrementAndDecrementGauge() {
        MetricsRegistry registry = new MetricsRegistry();

        registry.incrementGauge("active_connections", "Active connections");
        registry.incrementGauge("active_connections", "Active connections");
        registry.decrementGauge("active_connections", "Active connections");

        assertTrue(registry.renderPrometheusText().contains("active_connections 1"));
    }

    @Test
    public void testRenderPrometheusTextWithNoMetricsIsEmpty() {
        MetricsRegistry registry = new MetricsRegistry();

        assertEquals("", registry.renderPrometheusText());
    }

    @Test
    public void testCountersAndGaugesWithSameNameSpaceDoNotCollide() {
        MetricsRegistry registry = new MetricsRegistry();

        registry.incrementCounter("a_total", "A counter");
        registry.setGauge("z_gauge", "A gauge", 42);

        String rendered = registry.renderPrometheusText();
        assertTrue(rendered.indexOf("a_total") < rendered.indexOf("z_gauge"),
                "counters should render before gauges");
    }
}
