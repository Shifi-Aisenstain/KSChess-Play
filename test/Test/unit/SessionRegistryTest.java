package Test.unit;

import org.java_websocket.WebSocket;
import org.junit.jupiter.api.Test;
import server.net.PlayerSession;
import server.net.SessionRegistry;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

public class SessionRegistryTest {

    @Test
    public void testGetOrCreateCreatesOncePerSocket() throws Exception {
        SessionRegistry registry = new SessionRegistry(TestSupport.newActivityLogger());
        WebSocket socket = TestSupport.fakeWebSocket(true, new ArrayList<>());

        PlayerSession first = registry.getOrCreate(socket);
        PlayerSession second = registry.getOrCreate(socket);

        assertSame(first, second);
    }

    @Test
    public void testDifferentSocketsGetDifferentSessions() throws Exception {
        SessionRegistry registry = new SessionRegistry(TestSupport.newActivityLogger());
        WebSocket socketA = TestSupport.fakeWebSocket(true, new ArrayList<>());
        WebSocket socketB = TestSupport.fakeWebSocket(true, new ArrayList<>());

        assertNotSame(registry.getOrCreate(socketA), registry.getOrCreate(socketB));
    }

    @Test
    public void testGetReturnsNullForUnknownSocket() throws Exception {
        SessionRegistry registry = new SessionRegistry(TestSupport.newActivityLogger());
        assertNull(registry.get(TestSupport.fakeWebSocket(true, new ArrayList<>())));
    }

    @Test
    public void testRemoveForgetsTheSession() throws Exception {
        SessionRegistry registry = new SessionRegistry(TestSupport.newActivityLogger());
        WebSocket socket = TestSupport.fakeWebSocket(true, new ArrayList<>());
        registry.getOrCreate(socket);

        registry.remove(socket);

        assertNull(registry.get(socket));
    }
}
