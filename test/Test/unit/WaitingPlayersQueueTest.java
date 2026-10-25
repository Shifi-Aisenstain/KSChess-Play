package Test.unit;

import org.junit.jupiter.api.Test;
import server.matchmaking.InMemoryWaitingPlayersQueue;
import server.matchmaking.WaitingPlayer;
import server.matchmaking.WaitingPlayersQueue;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class WaitingPlayersQueueTest {

    @Test
    public void testAddIfAbsentIsIdempotentPerUserId() {
        WaitingPlayersQueue queue = new InMemoryWaitingPlayersQueue();

        queue.addIfAbsent(new WaitingPlayer(1, "alice", 1200, 1000));
        queue.addIfAbsent(new WaitingPlayer(1, "alice", 1200, 2000));

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
