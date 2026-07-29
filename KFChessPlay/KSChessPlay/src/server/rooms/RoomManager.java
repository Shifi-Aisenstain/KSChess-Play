package server.rooms;

import server.auth.User;
import server.auth.UserRepository;
import server.rating.RatingService;
import shared.bus.MessageBus;
import shared.bus.RoomBusSubjects;
import shared.eventbus.EventBus;
import shared.eventbus.events.GameEndedEvent;
import shared.protocol.Message;
import shared.protocol.MessageCodec;
import shared.protocol.MessageType;
import shared.protocol.payload.GameOverPayload;
import shared.protocol.payload.RoomAllocatedPayload;
import shared.protocol.payload.RoutedUpdatePayload;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Facade over room lifecycle: creation (spec 1.a "Room" button), joining by
 * code, and - once both colors are present - allocating a Game Server Shard to run the match.
 * Matchmaking-created rooms go through {@link #createRoomForMatch}, which differs only in that both
 * players are already known, so the session starts immediately instead of waiting for a second Join.
 *
 * <p>Since PLAN_split_6_services.md §3.2 (the WS Gateway extraction), this class - and the rest of
 * the not-yet-split monolith (Matchmaker, Auth) - no longer talks to client sockets at all: those
 * live in a separate {@code server.gateway.WsGatewayMain} process now, reached only over
 * {@code server.rpc.MonolithBusGateway}, the bus-facing front door this class sits behind. That's
 * why {@link #createRoom}/{@link #joinRoom} no longer take a {@code MessageSink} - there's nothing
 * in this process to attach one to anymore (see {@link Room}'s javadoc). {@link #startGameSession}
 * publishes {@code room.allocated} directly instead of building a
 * {@code server.game.RemoteGameSessionHandle} (retired in §3.2): that class existed only to relay
 * Shard traffic to a live {@code Room}'s sockets, and there are no sockets here to relay to anymore -
 * the WS Gateway now talks to the Shard directly (see {@code server.gateway.GatewayRoomSession}).
 */
public final class RoomManager {
    private final Map<String, Room> rooms = new ConcurrentHashMap<>();
    private final Map<Long, String> activeRoomByUserId = new ConcurrentHashMap<>();
    private final RoomIdGenerator idGenerator = new RoomIdGenerator();
    private final EventBus eventBus;
    private final RatingService ratingService;
    private final UserRepository userRepository;
    private final MessageBus bus;

    public RoomManager(EventBus eventBus, RatingService ratingService, UserRepository userRepository, MessageBus bus) {
        this.eventBus = eventBus;
        this.ratingService = ratingService;
        this.userRepository = userRepository;
        this.bus = bus;
    }

    /** Used by {@code LoginHandler}/{@code server.rpc.MonolithBusGateway}'s active-room lookup to
     *  detect "this user was mid-game and is reconnecting." */
    public Optional<String> getActiveRoomForUser(long userId) {
        return Optional.ofNullable(activeRoomByUserId.get(userId));
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

    public static final class CreateResult {
        public final boolean success;
        public final Room room;
        public final String errorMessage;

        private CreateResult(boolean success, Room room, String errorMessage) {
            this.success = success;
            this.room = room;
            this.errorMessage = errorMessage;
        }

        static CreateResult ok(Room room) { return new CreateResult(true, room, null); }
        static CreateResult fail(String message) { return new CreateResult(false, null, message); }
    }

    /**
     * {@code desiredRoomId} lets the creator pick a room name/ID instead of always getting a
     * generated one; blank/null still auto-generates. Room IDs are unique - {@link ConcurrentHashMap#putIfAbsent}
     * makes the check-and-reserve atomic, so two creators racing for the same custom ID can't both win.
     */
    public CreateResult createRoom(User creator, String desiredRoomId) {
        Room room;
        if (desiredRoomId == null || desiredRoomId.isBlank()) {
            room = reserveNewRoom();
        } else {
            String id = desiredRoomId.trim();
            room = reserveRoom(id);
            if (room == null) {
                return CreateResult.fail("Room ID already taken: " + id);
            }
        }
        room.reserveRole(creator);
        return CreateResult.ok(room);
    }

    public JoinResult joinRoom(String roomId, User user) {
        Room room = rooms.get(roomId);
        if (room == null) return JoinResult.fail("Room not found: " + roomId);

        Room.Role role = room.reserveRole(user);

        if (role == Room.Role.BLACK && !room.isStarted()) {
            startGameSession(room);
        }
        return JoinResult.ok(room, role);
    }

    /** Used by matchmaking, where both colors are decided the instant a pair is found. */
    public Room createRoomForMatch(User white, User black) {
        Room room = reserveNewRoom();
        room.reserveRole(white);
        room.reserveRole(black);
        startGameSession(room);
        return room;
    }

    public Optional<Room> getRoom(String roomId) {
        return Optional.ofNullable(rooms.get(roomId));
    }

    /** Lets a creator back out of a room they made via Cancel before an opponent ever joined. Once a
     *  game has started there is nothing left for this process to undo (no socket/sink lives here
     *  anymore - see the class javadoc), so this is a no-op past that point, same as before. */
    public void abandonRoom(String roomId, long userId) {
        Room room = rooms.get(roomId);
        if (room == null || room.isStarted()) return;
        // Nothing further to do: the only thing "abandoning" used to mean was detaching a sink,
        // which no longer exists here. The room stays reserved (matches the pre-§3.2 behavior,
        // which never freed the role either) so a duplicate Cancel is harmlessly idempotent.
    }

    private void startGameSession(Room room) {
        room.markStarted();
        User white = room.getUser('w');
        User black = room.getUser('b');
        activeRoomByUserId.put(white.getId(), room.getId());
        activeRoomByUserId.put(black.getId(), room.getId());

        // Standing in for the not-yet-split Game Allocator (§3.4): tells the Shard to start running
        // this room (PLAN_split_6_services.md §3.1), and - since §3.2 - also the signal every WS
        // Gateway instance waits for before it's safe to start publishing room.<roomId>.commands
        // (the Shard's own subscription to that subject is guaranteed to already exist by the time
        // this publish call returns/is observed - see GameShardRuntime).
        bus.publish(RoomBusSubjects.ROOM_ALLOCATED, MessageType.ROOM_ALLOCATED, new RoomAllocatedPayload(
                room.getId(), white.getId(), white.getUsername(), white.getElo(),
                black.getId(), black.getUsername(), black.getElo()));

        // Only remaining reason this process needs to know when a game ends at all: clearing
        // activeRoomByUserId so a later reconnect lookup doesn't point at a finished game. Before
        // §3.2 this piggybacked on GameEndedEvent already being republished onto this same eventBus
        // by RemoteGameSessionHandle (which relayed all Shard traffic here); now that nothing else in
        // this process needs the full relay, it's cheaper to just watch for GAME_OVER directly.
        MessageBus.Subscription[] subscriptionHolder = new MessageBus.Subscription[1];
        subscriptionHolder[0] = bus.subscribe(RoomBusSubjects.updates(room.getId()), (message, replyTo) -> {
            GameOverPayload gameOver = decodeIfGameOver(message);
            if (gameOver == null) return;
            activeRoomByUserId.remove(white.getId());
            activeRoomByUserId.remove(black.getId());
            eventBus.publish(new GameEndedEvent(room.getId(), gameOver.winnerColor, gameOver.reason,
                    gameOver.whiteUsername, gameOver.blackUsername));
            subscriptionHolder[0].unsubscribe();
        });
    }

    /** Null unless {@code message} is the GAME_OVER carried inside a {@code room.<roomId>.updates}
     *  {@link RoutedUpdatePayload} - everything else on that subject (STATE_UPDATE, GAME_EVENT) is
     *  irrelevant here and left alone. */
    private static GameOverPayload decodeIfGameOver(Message message) {
        RoutedUpdatePayload routed = MessageCodec.decodePayload(message, RoutedUpdatePayload.class);
        if (routed.innerType != MessageType.GAME_OVER) return null;
        return MessageCodec.decodeInnerPayload(routed.innerPayloadJson, GameOverPayload.class);
    }

    /**
     * Generates a fresh ID and atomically claims it in {@code rooms}. The previous version
     * checked {@code rooms.containsKey(id)} and only inserted afterwards - two threads creating
     * rooms at the same instant (e.g. matchmaking pairing several players in parallel) could both
     * see the id as free and both proceed, and the loser's {@code rooms.put} would silently
     * overwrite the winner's room, orphaning its game session. {@link ConcurrentHashMap#putIfAbsent}
     * makes "is this id free" and "claim it" a single atomic step, so that can no longer happen;
     * on the astronomically unlikely event of a generated-id collision, this just retries with a
     * new one instead of ever colliding.
     */
    private Room reserveNewRoom() {
        while (true) {
            Room room = reserveRoom(idGenerator.generate());
            if (room != null) return room;
        }
    }

    /** Atomically claims {@code id} for a brand-new room, or returns {@code null} if it's already taken. */
    private Room reserveRoom(String id) {
        Room room = new Room(id);
        return rooms.putIfAbsent(id, room) == null ? room : null;
    }
}
