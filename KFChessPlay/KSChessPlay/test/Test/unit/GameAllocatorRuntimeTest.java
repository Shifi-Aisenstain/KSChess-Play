package Test.unit;

import org.junit.jupiter.api.Test;
import server.allocator.GameAllocatorRuntime;
import server.allocator.ShardSelector;
import server.gateway.RoomLifecycleClient;
import server.rooms.RoomManager;
import shared.bus.InMemoryMessageBus;
import shared.bus.MessageBus;
import shared.bus.RoomBusSubjects;
import shared.eventbus.EventBus;
import shared.metrics.MetricsRegistry;
import shared.protocol.MessageCodec;
import shared.protocol.payload.RoomAllocatedPayload;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

public class GameAllocatorRuntimeTest {

    private MetricsRegistry metricsRegistry;

    private RoomLifecycleClient newAllocatorClient(MessageBus bus, ShardSelector shardSelector) {
        RoomManager roomManager = new RoomManager(new EventBus(), bus, shardSelector);
        metricsRegistry = new MetricsRegistry();
        new GameAllocatorRuntime(bus, roomManager, TestSupport.newActivityLoggerOrThrow(), metricsRegistry).start();
        return new RoomLifecycleClient(bus);
    }

    private RoomLifecycleClient newAllocatorClient(MessageBus bus) {
        return newAllocatorClient(bus, new ShardSelector(List.of("shard-1")));
    }

    @Test
    public void testCreateRoomThenJoinByCodeEndToEnd() {
        RoomLifecycleClient client = newAllocatorClient(new InMemoryMessageBus());

        RoomLifecycleClient.Result created = client.createRoom(1, "alice", 1200, null);
        assertTrue(created.success);
        assertEquals("WHITE", created.role);
        assertNotNull(created.roomId);

        RoomLifecycleClient.Result joined = client.joinRoom(2, "bob", 1210, created.roomId);
        assertTrue(joined.success);
        assertEquals("BLACK", joined.role);
        assertEquals(created.roomId, joined.roomId);
    }

    @Test
    public void testCreateRoomWithTakenCustomIdFailsOverBus() {
        RoomLifecycleClient client = newAllocatorClient(new InMemoryMessageBus());

        assertTrue(client.createRoom(1, "alice", 1200, "MYROOM").success);
        RoomLifecycleClient.Result second = client.createRoom(2, "bob", 1200, "MYROOM");

        assertFalse(second.success);
        assertNotNull(second.errorMessage);
    }

    @Test
    public void testJoinUnknownRoomFailsOverBus() {
        RoomLifecycleClient client = newAllocatorClient(new InMemoryMessageBus());

        RoomLifecycleClient.Result result = client.joinRoom(1, "alice", 1200, "NOPE");

        assertFalse(result.success);
    }

    @Test
    public void testActiveRoomLookupSupportsReconnectAfterBothPlayersJoin() {
        RoomLifecycleClient client = newAllocatorClient(new InMemoryMessageBus());

        RoomLifecycleClient.Result created = client.createRoom(1, "alice", 1200, "TRACKROOM");
        client.joinRoom(2, "bob", 1210, created.roomId);

        assertEquals("TRACKROOM", client.lookupActiveRoom(1).orElse(null));
        assertEquals("TRACKROOM", client.lookupActiveRoom(2).orElse(null));
        assertTrue(client.lookupActiveRoom(999).isEmpty());
    }

    @Test
    public void testCancelRoomBeforeGameStartIsSafeNoOp() {
        RoomLifecycleClient client = newAllocatorClient(new InMemoryMessageBus());
        client.createRoom(1, "alice", 1200, "ABANDON");

        assertDoesNotThrow(() -> client.cancelRoom(1, "ABANDON"));

        RoomLifecycleClient.Result joined = client.joinRoom(2, "bob", 1200, "ABANDON");
        assertTrue(joined.success);
    }

    @Test
    public void testRoundRobinPicksDifferentShardsAcrossSuccessiveAllocations() {
        MessageBus bus = new InMemoryMessageBus();
        List<String> capturedShardIds = new CopyOnWriteArrayList<>();
        bus.subscribe(RoomBusSubjects.ROOM_ALLOCATED, (message, replyTo) ->
                capturedShardIds.add(MessageCodec.decodePayload(message, RoomAllocatedPayload.class).shardId));

        RoomLifecycleClient client = newAllocatorClient(bus, new ShardSelector(List.of("shard-1", "shard-2")));

        RoomLifecycleClient.Result firstRoom = client.createRoom(1, "alice", 1200, null);
        client.joinRoom(2, "bob", 1200, firstRoom.roomId);
        RoomLifecycleClient.Result secondRoom = client.createRoom(3, "carol", 1200, null);
        client.joinRoom(4, "dave", 1200, secondRoom.roomId);

        assertEquals(List.of("shard-1", "shard-2"), capturedShardIds);
    }

    @Test
    public void testMetricsCountRoomLifecycleEvents() {
        RoomLifecycleClient client = newAllocatorClient(new InMemoryMessageBus());

        RoomLifecycleClient.Result created = client.createRoom(1, "alice", 1200, "METROOM");
        client.createRoom(2, "bob", 1200, "METROOM");
        client.joinRoom(3, "carol", 1200, created.roomId);
        client.joinRoom(4, "dave", 1200, "NOPE");
        client.cancelRoom(1, created.roomId);

        String rendered = metricsRegistry.renderPrometheusText();
        assertTrue(rendered.contains("allocator_rooms_created_total 1"));
        assertTrue(rendered.contains("allocator_rooms_create_rejected_total 1"));
        assertTrue(rendered.contains("allocator_rooms_joined_total 1"));
        assertTrue(rendered.contains("allocator_rooms_join_rejected_total 1"));
        assertTrue(rendered.contains("allocator_rooms_cancelled_total 1"));
    }
}
