package server.allocator;

import server.auth.User;
import server.logging.ServerActivityLogger;
import server.rooms.Room;
import server.rooms.RoomManager;
import shared.bus.MessageBus;
import shared.bus.RoomBusSubjects;
import shared.metrics.MetricsRegistry;
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

public final class GameAllocatorRuntime {
    private final MessageBus bus;
    private final RoomManager roomManager;
    private final ServerActivityLogger activityLogger;
    private final MetricsRegistry metricsRegistry;

    public GameAllocatorRuntime(MessageBus bus, RoomManager roomManager, ServerActivityLogger activityLogger,
                                 MetricsRegistry metricsRegistry) {
        this.bus = bus;
        this.roomManager = roomManager;
        this.activityLogger = activityLogger;
        this.metricsRegistry = metricsRegistry;
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
        if (result.success) {
            activityLogger.logTraffic("IN", request.userId, "ROOM_CREATE", "roomId=" + result.room.getId());
            metricsRegistry.incrementCounter("allocator_rooms_created_total", "Total rooms successfully created");
        } else {
            activityLogger.logTraffic("IN", request.userId, "ROOM_CREATE", "rejected: " + result.errorMessage);
            metricsRegistry.incrementCounter("allocator_rooms_create_rejected_total", "Total room-create requests rejected");
        }
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
        if (result.success) {
            activityLogger.logTraffic("IN", request.userId, "ROOM_JOIN", "roomId=" + request.roomId + " role=" + result.role);
            metricsRegistry.incrementCounter("allocator_rooms_joined_total", "Total successful room joins");
        } else {
            activityLogger.logTraffic("IN", request.userId, "ROOM_JOIN", "rejected: " + result.errorMessage);
            metricsRegistry.incrementCounter("allocator_rooms_join_rejected_total", "Total room-join requests rejected");
        }
        if (replyTo == null) return;
        if (result.success) {
            bus.reply(replyTo, MessageType.ROOM_JOINED, new RoomStatusPayload(result.room.getId(), result.role.name()));
        } else {
            bus.reply(replyTo, MessageType.ERROR, new ErrorPayload(result.errorMessage));
        }
    }

    private void onRoomCancel(Message message, String replyTo) {
        CancelRoomRequestPayload request = MessageCodec.decodePayload(message, CancelRoomRequestPayload.class);
        activityLogger.logTraffic("IN", request.userId, "ROOM_CANCEL", "roomId=" + request.roomId);
        metricsRegistry.incrementCounter("allocator_rooms_cancelled_total", "Total rooms cancelled/abandoned");
        roomManager.abandonRoom(request.roomId, request.userId);
    }

    private void onActiveRoomLookup(Message message, String replyTo) {
        ActiveRoomQueryPayload request = MessageCodec.decodePayload(message, ActiveRoomQueryPayload.class);
        if (replyTo == null) return;
        String roomId = roomManager.getActiveRoomForUser(request.userId).orElse(null);
        bus.reply(replyTo, MessageType.ACTIVE_ROOM_LOOKUP, new ActiveRoomReplyPayload(roomId));
    }
}
