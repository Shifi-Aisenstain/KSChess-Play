package Test.unit;

import org.junit.jupiter.api.Test;
import server.matchmaking.InMemoryWaitingPlayersQueue;
import server.matchmaking.WaitingPlayer;
import server.matchmaking.WaitingPlayersQueue;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Exercises the {@link WaitingPlayersQueue} contract (Server_Design.md step 4) against
 * {@link InMemoryWaitingPlayersQueue} - {@code RedisWaitingPlayersQueue} needs a live Redis server to
 * exercise for real (see pom.xml's jacoco excludes), the same reasoning {@code NatsMessageBus} is
 * excluded from unit tests in favor of {@code InMemoryMessageBus} (see {@code MessageBusTest}'s
 * javadoc) - both implementations must satisfy the exact same contract tested here.
 */
public class WaitingPlayersQueueTest {

    @Test
    public void testAddIfAbsentIsIdempotentPerUserId() {
        WaitingPlayersQueue queue = new InMemoryWaitingPlayersQueue();

        queue.addIfAbsent(new WaitingPlayer(1, "alice", 1200, 1000));
        queue.addIfAbsent(new WaitingPlayer(1, "alice", 1200, 2000)); // a racing duplicate enqueue

        assertEquals(1, queue.snapshot().size(), "a duplicate enqueue for the same userId must not create a second entry");
    }

    @Test
    public void testRemoveReturnsTrueOnlyWhenSomethingWasActuallyRemoved() {
        WaitingPlayersQueue queue = new InMemoryWaitingPlayersQueue();
        queue.addIfAbsent(new WaitingPlayer(1, "alice", 1200, 1000));

        assertTrue(queue.remove(1));
        assertFalse(queue.remove(1), "a second remove of the same userId (e.g. a racing caller) must report false");
    }

    @Test
    public void testSnapshotReflectsCurrentContents() {
        WaitingPlayersQueue queue = new InMemoryWaitingPlayersQueue();
        queue.addIfAbsent(new WaitingPlayer(1, "alice", 1200, 1000));
        queue.addIfAbsent(new WaitingPlayer(2, "bob", 1210, 1001));

        List<WaitingPlayer> snapshot = queue.snapshot();

        assertEquals(2, snapshot.size());
        assertTrue(snapshot.stream().anyMatch(p -> p.userId() == 1 && p.username().equals("alice")));
        assertTrue(snapshot.stream().anyMatch(p -> p.userId() == 2 && p.username().equals("bob")));
    }

    @Test
    public void testRemovedEntryIsGoneFromSnapshot() {
        WaitingPlayersQueue queue = new InMemoryWaitingPlayersQueue();
        queue.addIfAbsent(new WaitingPlayer(1, "alice", 1200, 1000));

        queue.remove(1);

        assertTrue(queue.snapshot().isEmpty());
    }
}
