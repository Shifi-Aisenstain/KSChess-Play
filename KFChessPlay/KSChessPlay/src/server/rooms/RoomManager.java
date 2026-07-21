package server.rooms;

import server.auth.User;
import server.auth.UserRepository;
import server.game.GameSession;
import server.net.MessageSink;
import server.rating.RatingService;
import shared.eventbus.EventBus;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Facade over room lifecycle: creation (spec 1.a "Room" button), joining by
 * code, and - once both colors are present - handing off to a fresh
 * {@link GameSession}. Matchmaking-created rooms go through
 * {@link #createRoomForMatch}, which differs only in that both players are
 * already known, so the session starts immediately instead of waiting for
 * a second Join.
 */
public final class RoomManager {
    private final Map<String, Room> rooms = new ConcurrentHashMap<>();
    private final RoomIdGenerator idGenerator = new RoomIdGenerator();
    private final EventBus eventBus;
    private final RatingService ratingService;
    private final UserRepository userRepository;

    public RoomManager(EventBus eventBus, RatingService ratingService, UserRepository userRepository) {
        this.eventBus = eventBus;
        this.ratingService = ratingService;
        this.userRepository = userRepository;
    }

    public static final class JoinResult {
        public final boolean success;
        public final Room room;
        public final Room.Role role;
        public final String errorMessage;

        private JoinResult(boolean success, Room room, Room.Role role, String errorMessage) {
            this.success = success;
            this.room = room;
            this.role = role;
            this.errorMessage = errorMessage;
        }

        static JoinResult ok(Room room, Room.Role role) { return new JoinResult(true, room, role, null); }
        static JoinResult fail(String message) { return new JoinResult(false, null, null, message); }
    }

    public Room createRoom(User creator, MessageSink sink) {
        Room room = new Room(uniqueId());
        room.reserveRole(creator);
        room.attachSink(creator.getId(), sink);
        rooms.put(room.getId(), room);
        return room;
    }

    public JoinResult joinRoom(String roomId, User user, MessageSink sink) {
        Room room = rooms.get(roomId);
        if (room == null) return JoinResult.fail("Room not found: " + roomId);

        Room.Role role = room.reserveRole(user);
        room.attachSink(user.getId(), sink);

        if (role == Room.Role.BLACK && room.getGameSession() == null) {
            startGameSession(room);
        }
        return JoinResult.ok(room, role);
    }

    /** Used by matchmaking, where both colors are decided the instant a pair is found. */
    public Room createRoomForMatch(User white, User black) {
        Room room = new Room(uniqueId());
        room.reserveRole(white);
        room.reserveRole(black);
        rooms.put(room.getId(), room);
        startGameSession(room);
        return room;
    }

    /** Called once the matched player's socket handler is ready to receive STATE_UPDATE pushes. */
    public void attachSink(String roomId, long userId, MessageSink sink) {
        Room room = rooms.get(roomId);
        if (room != null) room.attachSink(userId, sink);
    }

    public Optional<Room> getRoom(String roomId) {
        return Optional.ofNullable(rooms.get(roomId));
    }

    /** Lets a creator back out of a room they made via Cancel before an opponent ever joined. */
    public void abandonRoom(String roomId, long userId) {
        Room room = rooms.get(roomId);
        if (room != null && room.getGameSession() == null) {
            room.detachMember(userId);
        }
    }

    private void startGameSession(Room room) {
        User white = room.getUser('w');
        User black = room.getUser('b');
        GameSession session = new GameSession(room.getId(), room, white, black, eventBus, ratingService, userRepository);
        room.setGameSession(session);
        session.start();
    }

    private String uniqueId() {
        String id;
        do {
            id = idGenerator.generate();
        } while (rooms.containsKey(id));
        return id;
    }
}
