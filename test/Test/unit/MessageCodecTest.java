package Test.unit;

import org.junit.jupiter.api.Test;
import shared.protocol.Message;
import shared.protocol.MessageCodec;
import shared.protocol.MessageType;
import shared.protocol.payload.IdentifyPayload;
import shared.protocol.payload.MoveCommandPayload;
import shared.protocol.payload.EmptyPayload;
import shared.protocol.payload.ErrorPayload;
import shared.tracing.TraceContext;

import static org.junit.jupiter.api.Assertions.*;

public class MessageCodecTest {

    @org.junit.jupiter.api.BeforeEach
    public void clearTraceBeforeEachTest() {
        TraceContext.clear();
    }

    @org.junit.jupiter.api.AfterEach
    public void clearTraceAfterEachTest() {
        TraceContext.clear();
    }

    @Test
    public void testEncodeDecodeRoundTripPreservesPayloadFields() {
        MoveCommandPayload move = new MoveCommandPayload(1, 2, 3, 4);
        String wire = MessageCodec.encode(MessageType.MOVE, move);

        Message envelope = MessageCodec.decodeEnvelope(wire);
        assertEquals(MessageType.MOVE, envelope.getType());

        MoveCommandPayload decoded = MessageCodec.decodePayload(envelope, MoveCommandPayload.class);
        assertEquals(1, decoded.fromRow);
        assertEquals(2, decoded.fromCol);
        assertEquals(3, decoded.toRow);
        assertEquals(4, decoded.toCol);
    }

    @Test
    public void testEnvelopeCarriesPayloadAsNestedJsonString() {
        IdentifyPayload identify = new IdentifyPayload("tok-abc");
        String wire = MessageCodec.encode(MessageType.IDENTIFY, identify);

        assertTrue(wire.contains("\"payloadJson\":\""));
        assertTrue(wire.contains("tok-abc"));
    }

    @Test
    public void testDecodeUnknownFieldsAreIgnoredByType() {
        ErrorPayload error = new ErrorPayload("Room not found");
        String wire = MessageCodec.encode(MessageType.ERROR, error);
        Message envelope = MessageCodec.decodeEnvelope(wire);

        assertEquals(MessageType.ERROR, envelope.getType());
        ErrorPayload decoded = MessageCodec.decodePayload(envelope, ErrorPayload.class);
        assertEquals("Room not found", decoded.message);
    }

    @Test
    public void testEmptyPayloadRoundTrips() {
        String wire = MessageCodec.encode(MessageType.PING, EmptyPayload.INSTANCE);
        Message envelope = MessageCodec.decodeEnvelope(wire);
        assertEquals(MessageType.PING, envelope.getType());
        assertNotNull(MessageCodec.decodePayload(envelope, EmptyPayload.class));
    }

    @Test
    public void testAllMessageTypesSurviveEncodeDecode() {
        for (MessageType type : MessageType.values()) {
            String wire = MessageCodec.encode(type, EmptyPayload.INSTANCE);
            Message envelope = MessageCodec.decodeEnvelope(wire);
            assertEquals(type, envelope.getType());
        }
    }

    @Test
    public void testInnerPayloadRoundTripsWithoutEnvelope() {
        MoveCommandPayload move = new MoveCommandPayload(1, 2, 3, 4);
        String json = MessageCodec.encodeInnerPayload(move);

        assertFalse(json.contains("payloadJson"), "should be the bare payload, not wrapped in a Message envelope");

        MoveCommandPayload decoded = MessageCodec.decodeInnerPayload(json, MoveCommandPayload.class);
        assertEquals(1, decoded.fromRow);
        assertEquals(4, decoded.toCol);
    }

    @Test
    public void testEncodeCarriesTheCurrentTraceIdIntoTheEnvelope() {
        TraceContext.set("trace-42");

        String wire = MessageCodec.encode(MessageType.PING, EmptyPayload.INSTANCE);
        Message envelope = MessageCodec.decodeEnvelope(wire);

        assertEquals("trace-42", envelope.getTraceId());
    }

    @Test
    public void testEncodeGeneratesAFreshTraceIdWhenNoneIsCurrent() {
        String wire = MessageCodec.encode(MessageType.PING, EmptyPayload.INSTANCE);
        Message envelope = MessageCodec.decodeEnvelope(wire);

        assertNotNull(envelope.getTraceId());
    }

    @Test
    public void testTwoMessagesEncodedUnderTheSameTraceShareTheSameTraceId() {
        TraceContext.set("shared-trace");

        Message first = MessageCodec.decodeEnvelope(MessageCodec.encode(MessageType.MOVE, new MoveCommandPayload(1, 2, 3, 4)));
        Message second = MessageCodec.decodeEnvelope(MessageCodec.encode(MessageType.RESIGN, EmptyPayload.INSTANCE));

        assertEquals(first.getTraceId(), second.getTraceId());
    }
}
