package Test.unit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import server.auth.User;
import server.auth.UserRepository;
import server.net.MessageSink;
import server.rating.EloRatingService;
import server.rooms.Room;
import server.rooms.RoomManager;
import shared.eventbus.EventBus;
import shared.protocol.MessageType;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

public class RoomManagerTest {

    private static final class NoOpSink implements MessageSink {
        @Override public void send(MessageType type, Object payload) { }
    }

    /** Enough to satisfy GameSession's userRepository.updateElo(...) calls on finishGame - no DB needed. */
    private static final class InMemoryUserRepository implements UserRepository {
        private final Map<Long, StoredUser> byId = new HashMap<>();
        private final AtomicLong nextId = new AtomicLong(1);

        @Override public Optional<StoredUser> findByUsername(String username) { return Optional.empty(); }

        @Override public StoredUser create(String username, String passwordHash, String salt, int startingElo) {
            StoredUser u = new StoredUser(nextId.getAndIncrement(), username, passwordHash, salt, startingElo);
            byId.put(u.id, u);
            return u;
        }

        @Override public void updateElo(long userId, int newElo) { }
    }

    private RoomManager roomManager;
    private User alice;
    private User bob;
    private Room startedRoom;

    @BeforeEach
    public void setUp() {
        EventBus eventBus = new EventBus();
        roomManager = new RoomManager(eventBus, new EloRatingService(), new InMemoryUserRepository());
        alice = new User(1, "alice", 1200);
        bob = new User(2, "bob", 1210);
    }

    @AfterEach
    public void tearDown() {
        // A started GameSession spins up a background ticking thread (see GameSession.start()) -
        // resign it so the test doesn't leak a running ScheduledExecutorService.
        if (startedRoom != null && startedRoom.getGameSession() != null) {
            startedRoom.getGameSession().handleResign(alice.getId());
        }
    }

    @Test
    public void testCreateRoomReservesCreatorAsWhite() {
        RoomManager.CreateResult result = roomManager.createRoom(alice, new NoOpSink(), null);

        assertTrue(result.success);
        assertEquals(Room.Role.WHITE, result.room.getRoles().get(alice.getId()));
        assertNull(result.room.getGameSession(), "session shouldn't start until a second player joins");
    }

    @Test
    public void testCreateRoomWithBlankIdAutoGenerates() {
        RoomManager.CreateResult result = roomManager.createRoom(alice, new NoOpSink(), "  ");
        assertTrue(result.success);
        assertFalse(result.room.getId().isBlank());
    }

    @Test
    public void testCreateRoomWithCustomIdUsesIt() {
        RoomManager.CreateResult result = roomManager.createRoom(alice, new NoOpSink(), "MYROOM");
        assertTrue(result.success);
        assertEquals("MYROOM", result.room.getId());
    }

    @Test
    public void testCreateRoomWithTakenCustomIdFails() {
        roomManager.createRoom(alice, new NoOpSink(), "TAKEN");
        RoomManager.CreateResult second = roomManager.createRoom(bob, new NoOpSink(), "TAKEN");

        assertFalse(second.success);
        assertNotNull(second.errorMessage);
    }

    @Test
    public void testJoinUnknownRoomFails() {
        RoomManager.JoinResult result = roomManager.joinRoom("NOPE", alice, new NoOpSink());
        assertFalse(result.success);
    }

    @Test
    public void testSecondJoinStartsGameSession() {
        RoomManager.CreateResult created = roomManager.createRoom(alice, new NoOpSink(), "GAMEROOM");
        RoomManager.JoinResult joined = roomManager.joinRoom("GAMEROOM", bob, new NoOpSink());
        startedRoom = created.room;

        assertTrue(joined.success);
        assertEquals(Room.Role.BLACK, joined.role);
        assertNotNull(created.room.getGameSession());
    }

    @Test
    public void testActiveRoomTrackedForBothPlayersOnceGameStarts() {
        RoomManager.CreateResult created = roomManager.createRoom(alice, new NoOpSink(), "TRACKROOM");
        roomManager.joinRoom("TRACKROOM", bob, new NoOpSink());
        startedRoom = created.room;

        assertEquals(Optional.of("TRACKROOM"), roomManager.getActiveRoomForUser(alice.getId()));
        assertEquals(Optional.of("TRACKROOM"), roomManager.getActiveRoomForUser(bob.getId()));
    }

    @Test
    public void testNoActiveRoomForUserWithNoGame() {
        assertTrue(roomManager.getActiveRoomForUser(999).isEmpty());
    }

    @Test
    public void testAbandonRoomBeforeGameStartDetachesCreator() {
        roomManager.createRoom(alice, new NoOpSink(), "ABANDON");
        assertDoesNotThrow(() -> roomManager.abandonRoom("ABANDON", alice.getId()));
    }

    @Test
    public void testCreateRoomForMatchStartsSessionImmediately() {
        Room room = roomManager.createRoomForMatch(alice, bob);
        startedRoom = room;

        assertTrue(room.isFull());
        assertNotNull(room.getGameSession());
        assertEquals(Room.Role.WHITE, room.getRoles().get(alice.getId()));
        assertEquals(Room.Role.BLACK, room.getRoles().get(bob.getId()));
    }
}
