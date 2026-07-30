package Test.unit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import server.auth.User;
import server.auth.UserRepository;
import server.rating.EloRatingService;
import server.rooms.Room;
import server.rooms.RoomManager;
import shared.eventbus.EventBus;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

public class RoomManagerTest {

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

    private TestSupport.GatewayHarness harness;
    private RoomManager roomManager;
    private User alice;
    private User bob;
    private String startedRoomId;

    @BeforeEach
    public void setUp() {
        EventBus eventBus = new EventBus();
        harness = TestSupport.newGatewayHarness(eventBus, new EloRatingService(), new InMemoryUserRepository());
        roomManager = harness.roomManager;
        alice = new User(1, "alice", 1200);
        bob = new User(2, "bob", 1210);
    }

    @AfterEach
    public void tearDown() {

        if (startedRoomId != null) {
            harness.forceResign(startedRoomId, alice.getId());
        }
    }

    @Test
    public void testCreateRoomReservesCreatorAsWhite() {
        RoomManager.CreateResult result = roomManager.createRoom(alice, null);

        assertTrue(result.success);
        assertEquals(Room.Role.WHITE, result.room.getRoles().get(alice.getId()));
        assertFalse(result.room.isStarted(), "session shouldn't start until a second player joins");
    }

    @Test
    public void testCreateRoomWithBlankIdAutoGenerates() {
        RoomManager.CreateResult result = roomManager.createRoom(alice, "  ");
        assertTrue(result.success);
        assertFalse(result.room.getId().isBlank());
    }

    @Test
    public void testCreateRoomWithCustomIdUsesIt() {
        RoomManager.CreateResult result = roomManager.createRoom(alice, "MYROOM");
        assertTrue(result.success);
        assertEquals("MYROOM", result.room.getId());
    }

    @Test
    public void testCreateRoomWithTakenCustomIdFails() {
        roomManager.createRoom(alice, "TAKEN");
        RoomManager.CreateResult second = roomManager.createRoom(bob, "TAKEN");

        assertFalse(second.success);
        assertNotNull(second.errorMessage);
    }

    @Test
    public void testJoinUnknownRoomFails() {
        RoomManager.JoinResult result = roomManager.joinRoom("NOPE", alice);
        assertFalse(result.success);
    }

    @Test
    public void testSecondJoinStartsGameSession() {
        RoomManager.CreateResult created = roomManager.createRoom(alice, "GAMEROOM");
        RoomManager.JoinResult joined = roomManager.joinRoom("GAMEROOM", bob);
        startedRoomId = created.room.getId();

        assertTrue(joined.success);
        assertEquals(Room.Role.BLACK, joined.role);
        assertTrue(created.room.isStarted());
    }

    @Test
    public void testActiveRoomTrackedForBothPlayersOnceGameStarts() {
        RoomManager.CreateResult created = roomManager.createRoom(alice, "TRACKROOM");
        roomManager.joinRoom("TRACKROOM", bob);
        startedRoomId = created.room.getId();

        assertEquals(Optional.of("TRACKROOM"), roomManager.getActiveRoomForUser(alice.getId()));
        assertEquals(Optional.of("TRACKROOM"), roomManager.getActiveRoomForUser(bob.getId()));
    }

    @Test
    public void testNoActiveRoomForUserWithNoGame() {
        assertTrue(roomManager.getActiveRoomForUser(999).isEmpty());
    }

    @Test
    public void testAbandonRoomBeforeGameStartDetachesCreator() {
        roomManager.createRoom(alice, "ABANDON");
        assertDoesNotThrow(() -> roomManager.abandonRoom("ABANDON", alice.getId()));
    }

    @Test
    public void testCreateRoomForMatchStartsSessionImmediately() {
        Room room = roomManager.createRoomForMatch(alice, bob);
        startedRoomId = room.getId();

        assertTrue(room.isFull());
        assertTrue(room.isStarted());
        assertEquals(Room.Role.WHITE, room.getRoles().get(alice.getId()));
        assertEquals(Room.Role.BLACK, room.getRoles().get(bob.getId()));
    }
}
