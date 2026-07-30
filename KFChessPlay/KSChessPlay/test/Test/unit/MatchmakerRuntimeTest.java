package Test.unit;

import org.junit.jupiter.api.Test;
import server.allocator.GameAllocatorRuntime;
import server.allocator.ShardSelector;
import server.gateway.MatchmakingClient;
import server.gateway.RoomLifecycleClient;
import server.matchmaking.BusRoomAllocationClient;
import server.matchmaking.EloRangeMatchCriteria;
import server.matchmaking.MatchListener;
import server.matchmaking.MatchmakerRuntime;
import server.matchmaking.MatchmakingService;
import server.matchmaking.MatchmakingServiceImpl;
import server.rooms.RoomManager;
import shared.bus.InMemoryMessageBus;
import shared.bus.MessageBus;
import shared.eventbus.EventBus;

import java.util.List;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Exercises PLAN_split_6_services.md §3.3's own stated verification: "two clients enter the
 * matchmaking queue, get matched, and a game starts." {@link MatchmakingClient} (the same bus client
 * the real WS Gateway uses) talks to a real {@link MatchmakerRuntime} - running the unmodified
 * {@link MatchmakingServiceImpl} pairing algorithm - which in turn reaches a real
 * {@link GameAllocatorRuntime} only over the bus (via {@link BusRoomAllocationClient}), exactly like
 * production, over an {@link InMemoryMessageBus} standing in for a live NATS connection.
 */
public class MatchmakerRuntimeTest {

    private static final class RecordingListener implements MatchListener {
        volatile String roomId;
        volatile char color;
        volatile String opponentUsername;
        volatile int opponentElo;
        volatile boolean notFound;

        @Override
        public void onMatchFound(String roomId, char assignedColor, String opponentUsername, int opponentElo) {
            this.roomId = roomId;
            this.color = assignedColor;
            this.opponentUsername = opponentUsername;
            this.opponentElo = opponentElo;
        }

        @Override
        public void onNotFound() {
            notFound = true;
        }
    }

    private RoomManager roomManager;

    private MatchmakingClient newMatchmakerOnSharedBus(MessageBus bus) {
        roomManager = new RoomManager(new EventBus(), bus, new ShardSelector(List.of("shard-1")));
        new GameAllocatorRuntime(bus, roomManager).start();

        RoomLifecycleClient roomLifecycle = new RoomLifecycleClient(bus);
        MatchmakingService matchmakingService = new MatchmakingServiceImpl(
                new EloRangeMatchCriteria(100), new BusRoomAllocationClient(roomLifecycle));
        new MatchmakerRuntime(bus, matchmakingService).start();

        return new MatchmakingClient(bus);
    }

    /** The real scan loop ticks every 500ms (see MatchmakingServiceImpl.SCAN_INTERVAL_MS) - poll
     *  rather than sleep a fixed amount, so this isn't flaky under load. */
    private static void awaitTrue(BooleanSupplier condition, long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (!condition.getAsBoolean() && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
        }
    }

    @Test
    public void testTwoPlayersWithinEloRangeGetMatchedAndGameStarts() throws InterruptedException {
        MatchmakingClient client = newMatchmakerOnSharedBus(new InMemoryMessageBus());

        RecordingListener aliceListener = new RecordingListener();
        RecordingListener bobListener = new RecordingListener();
        client.enqueue(1, "alice", 1200, aliceListener);
        client.enqueue(2, "bob", 1210, bobListener);

        awaitTrue(() -> aliceListener.roomId != null && bobListener.roomId != null, 2000);

        assertNotNull(aliceListener.roomId, "alice should have been matched");
        assertEquals(aliceListener.roomId, bobListener.roomId, "both players should land in the same room");
        assertNotEquals(aliceListener.color, bobListener.color, "players must get opposite colors");
        assertEquals("bob", aliceListener.opponentUsername);
        assertEquals("alice", bobListener.opponentUsername);
        assertEquals(1210, aliceListener.opponentElo);

        // The room the Allocator created must actually be running (activeRoomByUserId populated),
        // i.e. this isn't just a MATCH_FOUND message with no real room/game session behind it.
        assertEquals(aliceListener.roomId, roomManager.getActiveRoomForUser(1).orElse(null));
        assertEquals(aliceListener.roomId, roomManager.getActiveRoomForUser(2).orElse(null));
    }

    @Test
    public void testLoneWaitingPlayerIsNotMatchedAgainstNobody() throws InterruptedException {
        MatchmakingClient client = newMatchmakerOnSharedBus(new InMemoryMessageBus());

        RecordingListener aliceListener = new RecordingListener();
        client.enqueue(1, "alice", 1200, aliceListener);

        Thread.sleep(700); // more than one scan cycle
        assertNull(aliceListener.roomId);
        assertFalse(aliceListener.notFound, "60s expiry hasn't elapsed, so no result should have fired yet");
    }

    @Test
    public void testCancelRemovesPlayerFromQueueBeforeMatch() throws InterruptedException {
        MatchmakingClient client = newMatchmakerOnSharedBus(new InMemoryMessageBus());

        RecordingListener aliceListener = new RecordingListener();
        client.enqueue(1, "alice", 1200, aliceListener);
        client.cancel(1);

        RecordingListener bobListener = new RecordingListener();
        client.enqueue(2, "bob", 1200, bobListener);

        Thread.sleep(700); // more than one scan cycle
        assertNull(aliceListener.roomId);
        assertNull(bobListener.roomId, "alice was cancelled before bob arrived, so there's no one left to pair bob with");
    }
}
