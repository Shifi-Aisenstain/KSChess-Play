package server.rooms;

import server.allocator.ActiveRoomRegistry;
import server.allocator.InMemoryActiveRoomRegistry;
import server.allocator.ShardSelector;
import server.auth.User;
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

public final class RoomManager {
    private final Map<String, Room> rooms = new ConcurrentHashMap<>();
    private final RoomIdGenerator idGenerator = new RoomIdGenerator();
    private final EventBus eventBus;
    private final MessageBus bus;
    private final ShardSelector shardSelector;
    private final ActiveRoomRegistry activeRoomRegistry;

    public RoomManager(EventBus eventBus, MessageBus bus, ShardSelector shardSelector) {
        this(eventBus, bus, shardSelector, new InMemoryActiveRoomRegistry());
    }

    public RoomManager(EventBus eventBus, MessageBus bus, ShardSelector shardSelector,
                        ActiveRoomRegistry activeRoomRegistry) {
        this.eventBus = eventBus;
        this.bus = bus;
        this.shardSelector = shardSelector;
        this.activeRoomRegistry = activeRoomRegistry;
    }

    public Optional<String> getActiveRoomForUser(long userId) {
        return activeRoomRegistry.get(userId);
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

    public void abandonRoom(String roomId, long userId) {
        Room room = rooms.get(roomId);
        if (room == null || room.isStarted()) return;

    }

    private void startGameSession(Room room) {
        room.markStarted();
        User white = room.getUser('w');
        User black = room.getUser('b');
        activeRoomRegistry.put(white.getId(), room.getId());
        activeRoomRegistry.put(black.getId(), room.getId());

        bus.publish(RoomBusSubjects.ROOM_ALLOCATED, MessageType.ROOM_ALLOCATED, new RoomAllocatedPayload(
                room.getId(), shardSelector.next(), white.getId(), white.getUsername(), white.getElo(),
                black.getId(), black.getUsername(), black.getElo()));

        MessageBus.Subscription[] subscriptionHolder = new MessageBus.Subscription[1];
        subscriptionHolder[0] = bus.subscribe(RoomBusSubjects.updates(room.getId()), (message, replyTo) -> {
            GameOverPayload gameOver = decodeIfGameOver(message);
            if (gameOver == null) return;
            activeRoomRegistry.remove(white.getId());
            activeRoomRegistry.remove(black.getId());
            eventBus.publish(new GameEndedEvent(room.getId(), gameOver.winnerColor, gameOver.reason,
                    gameOver.whiteUsername, gameOver.blackUsername));
            subscriptionHolder[0].unsubscribe();
        });
    }

    private static GameOverPayload decodeIfGameOver(Message message) {
        RoutedUpdatePayload routed = MessageCodec.decodePayload(message, RoutedUpdatePayload.class);
        if (routed.innerType != MessageType.GAME_OVER) return null;
        return MessageCodec.decodeInnerPayload(routed.innerPayloadJson, GameOverPayload.class);
    }

    private Room reserveNewRoom() {
        while (true) {
            Room room = reserveRoom(idGenerator.generate());
            if (room != null) return room;
        }
    }

    private Room reserveRoom(String id) {
        Room room = new Room(id);
        return rooms.putIfAbsent(id, room) == null ? room : null;
    }
}
