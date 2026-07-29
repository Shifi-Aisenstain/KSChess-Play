package server.rpc;

import server.auth.User;
import server.matchmaking.MatchListener;
import server.matchmaking.MatchRequest;
import server.matchmaking.MatchmakingService;
import server.rooms.Room;
import server.rooms.RoomManager;
import shared.bus.MessageBus;
import shared.bus.RoomBusSubjects;
import shared.protocol.Message;
import shared.protocol.MessageCodec;
import shared.protocol.MessageType;
import shared.protocol.payload.ActiveRoomQueryPayload;
import shared.protocol.payload.ActiveRoomReplyPayload;
import shared.protocol.payload.BusMatchFoundPayload;
import shared.protocol.payload.BusMatchNotFoundPayload;
import shared.protocol.payload.CancelMatchRequestPayload;
import shared.protocol.payload.CancelRoomRequestPayload;
import shared.protocol.payload.CreateRoomRequestPayload;
import shared.protocol.payload.ErrorPayload;
import shared.protocol.payload.JoinRoomRequestPayload;
import shared.protocol.payload.MatchEnqueueRequestPayload;
import shared.protocol.payload.RoomStatusPayload;

/**
 * The remaining monolith's bus-facing front door (PLAN_split_6_services.md §3.2): thin adapters
 * from the room-lifecycle/matchmaking bus subjects (§2) onto the real {@link RoomManager}/
 * {@link MatchmakingService} calls this process still makes in-process, exactly the same way it
 * always has. Nothing about {@code RoomManager}'s or {@code MatchmakingServiceImpl}'s own logic
 * changes here - this class only exists because the WS Gateway (where ROOM_CREATE/ROOM_JOIN/
 * ROOM_CANCEL/PLAY_REQUEST/PLAY_CANCEL used to be handled in-process, before §3.2 moved the sockets
 * out) can no longer call either service directly.
 *
 * <p>Standing in for two components that don't exist as separate processes yet: the Game Allocator
 * (room.create/room.join/room.cancel - real split is §3.4) and the Matchmaker
 * (matchmaking.requests/matchmaking.cancel/matchmaking.results - real split is §3.3).
 */
public final class MonolithBusGateway {
    private final MessageBus bus;
    private final RoomManager roomManager;
    private final MatchmakingService matchmakingService;

    public MonolithBusGateway(MessageBus bus, RoomManager roomManager, MatchmakingService matchmakingService) {
        this.bus = bus;
        this.roomManager = roomManager;
        this.matchmakingService = matchmakingService;
    }

    public void start() {
        bus.subscribe(RoomBusSubjects.ROOM_CREATE, this::onRoomCreate);
        bus.subscribe(RoomBusSubjects.ROOM_JOIN, this::onRoomJoin);
        bus.subscribe(RoomBusSubjects.ROOM_CANCEL, this::onRoomCancel);
        bus.subscribe(RoomBusSubjects.ACTIVE_ROOM_LOOKUP, this::onActiveRoomLookup);
        bus.subscribe(RoomBusSubjects.MATCHMAKING_REQUESTS, this::onMatchmakingRequest);
        bus.subscribe(RoomBusSubjects.MATCHMAKING_CANCEL, this::onMatchmakingCancel);
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

    private void onMatchmakingRequest(Message message, String replyTo) {
        MatchEnqueueRequestPayload request = MessageCodec.decodePayload(message, MatchEnqueueRequestPayload.class);
        matchmakingService.enqueue(new MatchRequest(request.userId, request.username, request.elo, new MatchListener() {
            @Override
            public void onMatchFound(String roomId, char assignedColor, String opponentUsername, int opponentElo) {
                bus.publish(RoomBusSubjects.MATCHMAKING_RESULTS, MessageType.MATCH_FOUND,
                        new BusMatchFoundPayload(request.userId, roomId, assignedColor, opponentUsername, opponentElo));
            }

            @Override
            public void onNotFound() {
                bus.publish(RoomBusSubjects.MATCHMAKING_RESULTS, MessageType.PLAY_NOT_FOUND,
                        new BusMatchNotFoundPayload(request.userId));
            }
        }));
    }

    private void onMatchmakingCancel(Message message, String replyTo) {
        CancelMatchRequestPayload request = MessageCodec.decodePayload(message, CancelMatchRequestPayload.class);
        matchmakingService.cancel(request.userId);
    }
}
