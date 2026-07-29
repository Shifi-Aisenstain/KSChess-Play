package Test.unit;

import models.Piece;
import models.Position;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import server.auth.User;
import server.game.GameEventBroadcaster;
import server.net.MessageSink;
import server.rating.EloRatingService;
import server.rooms.Room;
import server.rooms.RoomManager;
import shared.eventbus.EventBus;
import shared.eventbus.events.GameEndedEvent;
import shared.eventbus.events.GameStartedEvent;
import shared.eventbus.events.MoveExecutedEvent;
import shared.eventbus.events.PieceJumpedEvent;
import shared.protocol.MessageType;
import shared.protocol.payload.GameEventPayload;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

public class GameEventBroadcasterTest {

    private static final class InMemoryUserRepository implements server.auth.UserRepository {
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

    private static final class RecordingSink implements MessageSink {
        final List<GameEventPayload> received = new ArrayList<>();
        @Override public void send(MessageType type, Object payload) {
            if (payload instanceof GameEventPayload) received.add((GameEventPayload) payload);
        }
    }

    private EventBus eventBus;
    private RoomManager roomManager;
    private Room room;
    private RecordingSink sink;
    private User alice;

    private void setUp() {
        eventBus = new EventBus();
        roomManager = TestSupport.newRoomManagerWithLocalShard(eventBus, new EloRatingService(), new InMemoryUserRepository());
        alice = new User(1, "alice", 1200);
        User bob = new User(2, "bob", 1200);
        sink = new RecordingSink();
        // A real two-sided room (both getUser('w')/getUser('b') present): GameEndedEvent's own
        // RoomManager subscriber (see RoomManager's constructor) looks up both colors, and a
        // GameEndedEvent is only ever actually published for a fully-formed room in production.
        room = roomManager.createRoomForMatch(alice, bob);
        room.attachSink(alice.getId(), sink);
        new GameEventBroadcaster(eventBus, roomManager);
    }

    @AfterEach
    public void tearDown() {
        if (room != null && room.getGameSession() != null && !room.getGameSession().isOver()) {
            room.getGameSession().handleResign(alice.getId());
        }
    }

    @Test
    public void testGameStartedEventIsBroadcast() {
        setUp();
        eventBus.publish(new GameStartedEvent(room.getId(), "alice", "bob"));

        assertEquals(1, sink.received.size());
        assertEquals(GameEventPayload.Kind.GAME_STARTED, sink.received.get(0).kind);
    }

    @Test
    public void testGameEndedEventIsBroadcastWithWinnerName() {
        setUp();
        eventBus.publish(new GameEndedEvent(room.getId(), "WHITE", "checkmate", "alice", "bob"));

        assertEquals(GameEventPayload.Kind.GAME_ENDED, sink.received.get(0).kind);
        assertTrue(sink.received.get(0).message.contains("alice"));
    }

    @Test
    public void testGameEndedDrawHasGenericMessage() {
        setUp();
        eventBus.publish(new GameEndedEvent(room.getId(), null, "resignation", "alice", "bob"));

        assertEquals("Game over", sink.received.get(0).message);
    }

    @Test
    public void testMoveExecutedWithoutCaptureIsMoveExecutedKind() {
        setUp();
        Piece pawn = new Piece('w', 'P');
        eventBus.publish(new MoveExecutedEvent(room.getId(), pawn, new Position(6, 4), new Position(4, 4),
                null, "Pe4", 0, 0, "00:00.000|Pe4"));

        assertEquals(GameEventPayload.Kind.MOVE_EXECUTED, sink.received.get(0).kind);
    }

    @Test
    public void testMoveExecutedWithCaptureIsPieceCapturedKind() {
        setUp();
        Piece rook = new Piece('w', 'R');
        Piece capturedQueen = new Piece('b', 'Q');
        eventBus.publish(new MoveExecutedEvent(room.getId(), rook, new Position(0, 0), new Position(0, 4),
                capturedQueen, "Rxe8", 9, 0, "00:01.000|Rxe8"));

        assertEquals(GameEventPayload.Kind.PIECE_CAPTURED, sink.received.get(0).kind);
        assertEquals('Q', sink.received.get(0).capturedType);
    }

    @Test
    public void testPieceJumpedEventIsBroadcast() {
        setUp();
        eventBus.publish(new PieceJumpedEvent(room.getId(), new Piece('w', 'K'), new Position(7, 3)));

        assertEquals(GameEventPayload.Kind.PIECE_JUMPED, sink.received.get(0).kind);
    }

    @Test
    public void testEventForUnknownRoomIsSilentlyIgnored() {
        setUp();
        assertDoesNotThrow(() -> eventBus.publish(new GameStartedEvent("NO_SUCH_ROOM", "x", "y")));
        assertTrue(sink.received.isEmpty());
    }
}
