package Test.unit;

import org.junit.jupiter.api.Test;
import server.dispatch.MessageDispatcher;
import server.net.PlayerSession;
import shared.protocol.Message;
import shared.protocol.MessageCodec;
import shared.protocol.MessageType;
import shared.protocol.payload.EmptyPayload;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class MessageDispatcherTest {

    private Message envelope(MessageType type) {
        return MessageCodec.decodeEnvelope(MessageCodec.encode(type, EmptyPayload.INSTANCE));
    }

    @Test
    public void testDispatchesToRegisteredHandler() throws Exception {
        MessageDispatcher dispatcher = new MessageDispatcher();
        List<Boolean> called = new ArrayList<>();
        dispatcher.register(MessageType.PING, (session, message) -> called.add(true));

        List<String> sent = new ArrayList<>();
        PlayerSession session = new PlayerSession(TestSupport.fakeWebSocket(true, sent), TestSupport.newActivityLogger());
        dispatcher.dispatch(session, envelope(MessageType.PING));

        assertEquals(List.of(true), called);
    }

    @Test
    public void testUnregisteredTypeSendsError() throws Exception {
        MessageDispatcher dispatcher = new MessageDispatcher();
        List<String> sent = new ArrayList<>();
        PlayerSession session = new PlayerSession(TestSupport.fakeWebSocket(true, sent), TestSupport.newActivityLogger());

        dispatcher.dispatch(session, envelope(MessageType.MOVE));

        assertEquals(1, sent.size());
        assertTrue(sent.get(0).contains("Unsupported message type"));
    }

    @Test
    public void testHandlerExceptionIsCaughtAndReportedAsError() throws Exception {
        MessageDispatcher dispatcher = new MessageDispatcher();
        dispatcher.register(MessageType.PING, (session, message) -> { throw new RuntimeException("kaboom"); });

        List<String> sent = new ArrayList<>();
        PlayerSession session = new PlayerSession(TestSupport.fakeWebSocket(true, sent), TestSupport.newActivityLogger());

        assertDoesNotThrow(() -> dispatcher.dispatch(session, envelope(MessageType.PING)));
        assertEquals(1, sent.size());
        assertTrue(sent.get(0).contains("kaboom"));
    }
}
