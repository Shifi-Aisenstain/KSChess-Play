package Test.unit;

import org.junit.jupiter.api.Test;
import server.auth.User;
import server.net.PlayerSession;
import shared.protocol.MessageType;
import shared.protocol.payload.ErrorPayload;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class PlayerSessionTest {

    @Test
    public void testSendEncodesAndForwardsToOpenSocket() throws Exception {
        List<String> sent = new ArrayList<>();
        PlayerSession session = new PlayerSession(TestSupport.fakeWebSocket(true, sent), TestSupport.newActivityLogger());

        session.send(MessageType.ERROR, new ErrorPayload("boom"));

        assertEquals(1, sent.size());
        assertTrue(sent.get(0).contains("boom"));
    }

    @Test
    public void testSendOnClosedSocketIsANoOp() throws Exception {
        List<String> sent = new ArrayList<>();
        PlayerSession session = new PlayerSession(TestSupport.fakeWebSocket(false, sent), TestSupport.newActivityLogger());

        session.send(MessageType.PONG, shared.protocol.payload.EmptyPayload.INSTANCE);

        assertTrue(sent.isEmpty());
    }

    @Test
    public void testUserAndRoomIdAreStoredAndRetrievable() throws Exception {
        PlayerSession session = new PlayerSession(TestSupport.fakeWebSocket(true, new ArrayList<>()), TestSupport.newActivityLogger());
        assertNull(session.getUser());
        assertNull(session.getCurrentRoomId());

        User user = new User(1, "alice", 1200);
        session.setUser(user);
        session.setCurrentRoomId("ROOM1");

        assertEquals(user, session.getUser());
        assertEquals("ROOM1", session.getCurrentRoomId());
    }

    @Test
    public void testGetConnectionReturnsTheOriginalSocket() throws Exception {
        var socket = TestSupport.fakeWebSocket(true, new ArrayList<>());
        PlayerSession session = new PlayerSession(socket, TestSupport.newActivityLogger());
        assertSame(socket, session.getConnection());
    }
}
