package Test.unit;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import shared.tracing.JaegerReporter;
import shared.tracing.Span;

import static org.junit.jupiter.api.Assertions.*;

public class SpanAndJaegerReporterTest {

    @Test
    public void testSpanStartGeneratesIdsAndCarriesTheGivenTraceId() {
        Span span = Span.start("my-trace-id", "gateway.MOVE");

        assertEquals("my-trace-id", span.traceId);
        assertEquals("gateway.MOVE", span.name);
        assertNotNull(span.spanId);
        assertTrue(span.startEpochMicros > 0);
    }

    @Test
    public void testSpanStartGeneratesATraceIdWhenGivenNull() {
        Span span = Span.start(null, "apigateway.login");

        assertNotNull(span.traceId);
    }

    @Test
    public void testTagIsChainableAndStored() {
        Span span = Span.start("t1", "op").tag("message.type", "MOVE").tag("user.id", "42");

        assertEquals("MOVE", span.tags().get("message.type"));
        assertEquals("42", span.tags().get("user.id"));
    }

    @Test
    public void testFinishComputesAPositiveDuration() throws InterruptedException {
        Span span = Span.start("t1", "op");
        Thread.sleep(2);

        long duration = span.finish();

        assertTrue(duration > 0);
        assertEquals(duration, span.durationMicros());
    }

    @Test
    public void testZipkinJsonContainsCoreFields() {
        Span span = Span.start("abc123", "apigateway.login").tag("outcome", "success");
        span.finish();

        String json = JaegerReporter.toZipkinJson(span, "apigateway");
        JsonArray array = JsonParser.parseString(json).getAsJsonArray();
        assertEquals(1, array.size());

        JsonObject spanJson = array.get(0).getAsJsonObject();
        assertEquals("abc123", spanJson.get("traceId").getAsString());
        assertEquals(span.spanId, spanJson.get("id").getAsString());
        assertEquals("apigateway.login", spanJson.get("name").getAsString());
        assertEquals("apigateway", spanJson.getAsJsonObject("localEndpoint").get("serviceName").getAsString());
        assertEquals("success", spanJson.getAsJsonObject("tags").get("outcome").getAsString());
        assertTrue(spanJson.get("duration").getAsLong() > 0);
    }

    @Test
    public void testZipkinJsonOmitsTagsKeyWhenNoTagsWereAdded() {
        Span span = Span.start("abc123", "gateway.PING");
        span.finish();

        String json = JaegerReporter.toZipkinJson(span, "gateway");
        JsonObject spanJson = JsonParser.parseString(json).getAsJsonArray().get(0).getAsJsonObject();

        assertFalse(spanJson.has("tags"), "a span with no tags should not send an empty tags object");
    }

    @Test
    public void testReportDoesNotThrowEvenWhenTheJaegerEndpointIsUnreachable() {
        JaegerReporter reporter = new JaegerReporter("http://localhost:1/not-a-real-jaeger");
        Span span = Span.start("t1", "op");

        assertDoesNotThrow(() -> reporter.report(span, "test-service"),
                "reporting must be fire-and-forget - an unreachable Jaeger must never break the caller");
    }
}
