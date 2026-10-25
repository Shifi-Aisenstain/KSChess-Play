package Test.unit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import shared.tracing.TraceContext;

import static org.junit.jupiter.api.Assertions.*;

public class TraceContextTest {

    @BeforeEach
    public void clearBeforeEachTest() {
        TraceContext.clear();
    }

    @AfterEach
    public void clearAfterEachTest() {
        TraceContext.clear();
    }

    @Test
    public void testNewIdProducesDistinct32CharHexStrings() {
        String first = TraceContext.newId();
        String second = TraceContext.newId();

        assertEquals(32, first.length());
        assertTrue(first.matches("[0-9a-f]{32}"));
        assertNotEquals(first, second);
    }

    @Test
    public void testNewSpanIdProduces16CharHexString() {
        String spanId = TraceContext.newSpanId();

        assertEquals(16, spanId.length());
        assertTrue(spanId.matches("[0-9a-f]{16}"));
    }

    @Test
    public void testCurrentIsNullWhenNothingSet() {
        assertNull(TraceContext.current());
    }

    @Test
    public void testSetThenCurrentReturnsTheSameId() {
        TraceContext.set("abc123");

        assertEquals("abc123", TraceContext.current());
    }

    @Test
    public void testCurrentOrNewGeneratesAFreshIdWhenNoneIsSet() {
        String generated = TraceContext.currentOrNew();

        assertNotNull(generated);
        assertEquals(generated, TraceContext.current(),
                "currentOrNew must not just return a fresh id, it must also become the current one so later encodes on the same thread agree");
    }

    @Test
    public void testCurrentOrNewReusesAnExistingId() {
        TraceContext.set("existing-trace");

        assertEquals("existing-trace", TraceContext.currentOrNew());
    }

    @Test
    public void testRunWithTraceSetsAndRestoresPreviousValue() {
        TraceContext.set("outer");

        TraceContext.runWithTrace("inner", () -> assertEquals("inner", TraceContext.current()));

        assertEquals("outer", TraceContext.current(), "runWithTrace must restore whatever was current before it ran");
    }

    @Test
    public void testRunWithTraceClearsWhenNothingWasSetBefore() {
        TraceContext.runWithTrace("only-this-scope", () -> assertEquals("only-this-scope", TraceContext.current()));

        assertNull(TraceContext.current());
    }

    @Test
    public void testRunWithTraceGeneratesAnIdWhenGivenNull() {
        TraceContext.runWithTrace(null, () -> assertNotNull(TraceContext.current()));
    }
}
