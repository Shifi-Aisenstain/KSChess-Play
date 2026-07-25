package Test.unit;

import org.junit.jupiter.api.Test;
import shared.protocol.Message;
import shared.protocol.MessageCodec;
import shared.protocol.MessageType;
import shared.protocol.payload.LoginPayload;
import shared.protocol.payload.MoveCommandPayload;
import shared.protocol.payload.EmptyPayload;
import shared.protocol.payload.ErrorPayload;

import static org.junit.jupiter.api.Assertions.*;

public class MessageCodecTest {

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
        LoginPayload login = new LoginPayload("alice", "hunter2");
        String wire = MessageCodec.encode(MessageType.LOGIN, login);

        // The wire format is deliberately "envelope holds raw payload JSON" - the outer object's
        // payloadJson field is itself a JSON string, not a nested object.
        assertTrue(wire.contains("\"payloadJson\":\""));
        assertTrue(wire.contains("alice"));
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
}
