package Test.unit;

import org.junit.jupiter.api.Test;
import server.auth.InMemorySessionStore;
import server.auth.SessionInfo;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

public class InMemorySessionStoreTest {

    @Test
    public void testCreateSessionThenValidateReturnsTheSameIdentity() {
        InMemorySessionStore store = new InMemorySessionStore();

        String token = store.createSession(42, "alice", 1200);
        Optional<SessionInfo> info = store.validate(token);

        assertTrue(info.isPresent());
        assertEquals(42, info.get().userId);
        assertEquals("alice", info.get().username);
        assertEquals(1200, info.get().elo);
    }

    @Test
    public void testUnknownTokenIsRejected() {
        InMemorySessionStore store = new InMemorySessionStore();

        assertTrue(store.validate("never-issued").isEmpty());
    }

    @Test
    public void testNullTokenIsRejected() {
        InMemorySessionStore store = new InMemorySessionStore();

        assertTrue(store.validate(null).isEmpty());
    }

    @Test
    public void testEachCreatedSessionGetsAUniqueToken() {
        InMemorySessionStore store = new InMemorySessionStore();

        String tokenA = store.createSession(1, "alice", 1200);
        String tokenB = store.createSession(2, "bob", 1200);

        assertNotEquals(tokenA, tokenB);
    }

    @Test
    public void testExpiredSessionIsRejectedAndRemoved() throws InterruptedException {
        InMemorySessionStore store = new InMemorySessionStore(10);

        String token = store.createSession(1, "alice", 1200);
        Thread.sleep(30);

        assertTrue(store.validate(token).isEmpty(), "a session past its TTL must no longer validate");
        assertTrue(store.validate(token).isEmpty(), "a second lookup after expiry must still fail, not resurrect the entry");
    }
}
