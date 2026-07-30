package server.allocator;

import server.auth.User;
import server.rooms.Room;
import server.rooms.RoomManager;
import shared.bus.MessageBus;
import shared.bus.RoomBusSubjects;
import shared.protocol.Message;
import shared.protocol.MessageCodec;
import shared.protocol.MessageType;
import shared.protocol.payload.ActiveRoomQueryPayload;
import shared.protocol.payload.ActiveRoomReplyPayload;
import shared.protocol.payload.CancelRoomRequestPayload;
import shared.protocol.payload.CreateRoomRequestPayload;
import shared.protocol.payload.ErrorPayload;
import shared.protocol.payload.JoinRoomRequestPayload;
import shared.protocol.payload.RoomStatusPayload;

/**
 * The actual "own room lifecycle" logic of the Game Allocator (PLAN_split_6_services.md §3.4),
 * factored out of {@link GameAllocatorMain} so it can be exercised directly against a
 * {@code shared.bus.InMemoryMessageBus} in tests, the same split as {@code server.shard.GameShardRuntime}
 * vs. {@code GameServerShardMain}. Answers exactly the four room-lifecycle subjects
 * {@code server.gateway.RoomLifecycleClient} calls - moved here verbatim from the now-retired
 * {@code server.rpc.MonolithBusGateway}, which stood in for this process before the real split.
 */
public final class GameAllocatorRuntime {
    private final MessageBus bus;
    private final RoomManager roomManager;

    public GameAllocatorRuntime(MessageBus bus, RoomManager roomManager) {
        this.bus = bus;
        this.roomManager = roomManager;
    }

    public void start() {
        bus.subscribe(RoomBusSubjects.ROOM_CREATE, this::onRoomCreate);
        bus.subscribe(RoomBusSubjects.ROOM_JOIN, this::onRoomJoin);
        bus.subscribe(RoomBusSubjects.ROOM_CANCEL, this::onRoomCancel);
        bus.subscribe(RoomBusSubjects.ACTIVE_ROOM_LOOKUP, this::onActiveRoomLookup);
    }

    private void onRoomCreate(Message message, String replyTo) {
        CreateRoomRequestPayload request = MessageCodec.decodePayload(message, CreateRoomRequestPayload.class);
        User user = new User(request.userId, request.username, request.elo);
        RoomManager.CreateResult result = roomManager.createRoom(user, request.desiredRoomId);
        if (replyTo == null) return;
        if (result.success) {
            bus.reply(replyTo, MessageType.ROOM_CREATED, new RoomStatusPayload(result.room.getId(), Room.Role.WHITE.name()));
        } else {
            bus.reply(replyTo, MessageType.ERROR, new ErrorPayload(result.errorMessage));
        }
    }

    private void onRoomJoin(Message message, String replyTo) {
        JoinRoomRequestPayload request = MessageCodec.decodePayload(message, JoinRoomRequestPayload.class);
        User user = new User(request.userId, request.username, request.elo);
        RoomManager.JoinResult result = roomManager.joinRoom(request.roomId, user);
        if (replyTo == null) return;
        if (result.success) {
            bus.reply(replyTo, MessageType.ROOM_JOINED, new RoomStatusPayload(result.room.getId(), result.role.name()));
        } else {
            bus.reply(replyTo, MessageType.ERROR, new ErrorPayload(result.errorMessage));
        }
    }

    private void onRoomCancel(Message message, String replyTo) {
        CancelRoomRequestPayload request = MessageCodec.decodePayload(message, CancelRoomRequestPayload.class);
        roomManager.abandonRoom(request.roomId, request.userId);
    }

    private void onActiveRoomLookup(Message message, String replyTo) {
        ActiveRoomQueryPayload request = MessageCodec.decodePayload(message, ActiveRoomQueryPayload.class);
        if (replyTo == null) return;
        String roomId = roomManager.getActiveRoomForUser(request.userId).orElse(null);
        bus.reply(replyTo, MessageType.ACTIVE_ROOM_LOOKUP, new ActiveRoomReplyPayload(roomId));
    }
}
