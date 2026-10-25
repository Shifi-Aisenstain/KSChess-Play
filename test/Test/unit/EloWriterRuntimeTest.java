package Test.unit;

import org.junit.jupiter.api.Test;
import server.auth.UserRepository;
import server.persistence.EloWriterRuntime;
import shared.bus.InMemoryMessageBus;
import shared.bus.MessageBus;
import shared.bus.RoomBusSubjects;
import shared.metrics.MetricsRegistry;
import shared.protocol.MessageType;
import shared.protocol.payload.EloUpdateRequestedPayload;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

public class EloWriterRuntimeTest {

    private static final class RecordingUserRepository implements UserRepository {
        final AtomicLong nextId = new AtomicLong(1);
        final Map<Long, StoredUser> byId = new ConcurrentHashMap<>();
        final CopyOnWriteArrayList<Map<Long, Integer>> batchCalls = new CopyOnWriteArrayList<>();

        @Override public Optional<StoredUser> findByUsername(String username) { return Optional.empty(); }
        @Override public StoredUser create(String username, String passwordHash, String salt, int startingElo) {
            StoredUser u = new StoredUser(nextId.getAndIncrement(), username, passwordHash, salt, startingElo);
            byId.put(u.id, u);
            return u;
        }
        @Override public void updateElo(long userId, int newElo) {

            batchCalls.add(Map.of(userId, newElo));
        }
        @Override public void updateEloBatch(Map<Long, Integer> newEloByUserId) {
            batchCalls.add(new HashMap<>(newEloByUserId));
        }
    }

    private static void awaitTrue(java.util.function.BooleanSupplier condition, long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (!condition.getAsBoolean() && System.currentTimeMillis() < deadline) {
            Thread.sleep(10);
        }
    }

    @Test
    public void testBufferedUpdatesAreFlushedAsOneBatch() throws InterruptedException {
        MessageBus bus = new InMemoryMessageBus();
        RecordingUserRepository userRepository = new RecordingUserRepository();
        new EloWriterRuntime(bus, userRepository, 50).start();

        bus.publish(RoomBusSubjects.ELO_UPDATES, MessageType.ELO_UPDATE_REQUESTED, new EloUpdateRequestedPayload(1, 1350));
        bus.publish(RoomBusSubjects.ELO_UPDATES, MessageType.ELO_UPDATE_REQUESTED, new EloUpdateRequestedPayload(2, 1050));

        awaitTrue(() -> !userRepository.batchCalls.isEmpty(), 2000);

        assertEquals(1, userRepository.batchCalls.size(), "both updates published before the flush tick should land in the same batch");
        Map<Long, Integer> batch = userRepository.batchCalls.get(0);
        assertEquals(1350, batch.get(1L));
        assertEquals(1050, batch.get(2L));
    }

    @Test
    public void testLaterUpdateForSameUserWinsWithinAFlushWindow() throws InterruptedException {
        MessageBus bus = new InMemoryMessageBus();
        RecordingUserRepository userRepository = new RecordingUserRepository();
        new EloWriterRuntime(bus, userRepository, 50).start();

        bus.publish(RoomBusSubjects.ELO_UPDATES, MessageType.ELO_UPDATE_REQUESTED, new EloUpdateRequestedPayload(1, 1180));
        bus.publish(RoomBusSubjects.ELO_UPDATES, MessageType.ELO_UPDATE_REQUESTED, new EloUpdateRequestedPayload(1, 1220));

        awaitTrue(() -> !userRepository.batchCalls.isEmpty(), 2000);

        assertEquals(1220, userRepository.batchCalls.get(0).get(1L));
    }

    @Test
    public void testNoFlushCallWhenNothingWasPublished() throws InterruptedException {
        MessageBus bus = new InMemoryMessageBus();
        RecordingUserRepository userRepository = new RecordingUserRepository();
        new EloWriterRuntime(bus, userRepository, 30).start();

        Thread.sleep(150);

        assertTrue(userRepository.batchCalls.isEmpty(), "an empty buffer should never call updateEloBatch");
    }

    @Test
    public void testMetricsCountFlushedUpdatesAndFailures() throws InterruptedException {
        MessageBus bus = new InMemoryMessageBus();
        RecordingUserRepository userRepository = new RecordingUserRepository();
        MetricsRegistry metricsRegistry = new MetricsRegistry();
        new EloWriterRuntime(bus, userRepository, 50, metricsRegistry).start();

        bus.publish(RoomBusSubjects.ELO_UPDATES, MessageType.ELO_UPDATE_REQUESTED, new EloUpdateRequestedPayload(1, 1350));
        bus.publish(RoomBusSubjects.ELO_UPDATES, MessageType.ELO_UPDATE_REQUESTED, new EloUpdateRequestedPayload(2, 1050));

        awaitTrue(() -> !userRepository.batchCalls.isEmpty(), 2000);

        assertTrue(metricsRegistry.renderPrometheusText().contains("elowriter_elo_updates_written_total 2"));
    }

    @Test
    public void testMetricsCountFlushFailuresWhenRepositoryThrows() throws InterruptedException {
        MessageBus bus = new InMemoryMessageBus();
        UserRepository throwingRepository = new UserRepository() {
            @Override public Optional<StoredUser> findByUsername(String username) { return Optional.empty(); }
            @Override public StoredUser create(String username, String passwordHash, String salt, int startingElo) {
                throw new UnsupportedOperationException();
            }
            @Override public void updateElo(long userId, int newElo) { }
            @Override public void updateEloBatch(Map<Long, Integer> newEloByUserId) {
                throw new IllegalStateException("db unavailable");
            }
        };
        MetricsRegistry metricsRegistry = new MetricsRegistry();
        new EloWriterRuntime(bus, throwingRepository, 50, metricsRegistry).start();

        bus.publish(RoomBusSubjects.ELO_UPDATES, MessageType.ELO_UPDATE_REQUESTED, new EloUpdateRequestedPayload(1, 1350));

        awaitTrue(() -> metricsRegistry.renderPrometheusText().contains("elowriter_flush_failures_total"), 2000);

        assertTrue(metricsRegistry.renderPrometheusText().contains("elowriter_flush_failures_total 1"));
    }
}
