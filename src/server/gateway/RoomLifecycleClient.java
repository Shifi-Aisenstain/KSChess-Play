package server.gateway;

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

import java.time.Duration;
import java.util.Optional;

public final class RoomLifecycleClient {
    private static final Duration TIMEOUT = Duration.ofSeconds(3);

    private final MessageBus bus;

    public RoomLifecycleClient(MessageBus bus) {
        this.bus = bus;
    }

    public static final class Result {
        public final boolean success;
        public final String roomId;
        public final String role;
        public final String errorMessage;

        private Result(boolean success, String roomId, String role, String errorMessage) {
            this.success = success;
            this.roomId = roomId;
            this.role = role;
            this.errorMessage = errorMessage;
        }

        static Result ok(String roomId, String role) { return new Result(true, roomId, role, null); }
        static Result fail(String message) { return new Result(false, null, null, message); }
    }

    public Result createRoom(long userId, String username, int elo, String desiredRoomId) {
        return decodeReply(bus.request(RoomBusSubjects.ROOM_CREATE, MessageType.ROOM_CREATE,
                new CreateRoomRequestPayload(userId, username, elo, desiredRoomId), TIMEOUT));
    }

    public Result joinRoom(long userId, String username, int elo, String roomId) {
        return decodeReply(bus.request(RoomBusSubjects.ROOM_JOIN, MessageType.ROOM_JOIN,
                new JoinRoomRequestPayload(userId, username, elo, roomId), TIMEOUT));
    }

    public void cancelRoom(long userId, String roomId) {
        bus.publish(RoomBusSubjects.ROOM_CANCEL, MessageType.ROOM_CANCEL, new CancelRoomRequestPayload(userId, roomId));
    }

    public Optional<String> lookupActiveRoom(long userId) {
        Optional<Message> reply = bus.request(RoomBusSubjects.ACTIVE_ROOM_LOOKUP, MessageType.ACTIVE_ROOM_LOOKUP,
                new ActiveRoomQueryPayload(userId), TIMEOUT);
        if (reply.isEmpty()) return Optional.empty();
        return Optional.ofNullable(MessageCodec.decodePayload(reply.get(), ActiveRoomReplyPayload.class).roomId);
    }

    private static Result decodeReply(Optional<Message> reply) {
        if (reply.isEmpty()) return Result.fail("Room service didn't respond in time - please try again.");
        Message message = reply.get();
        if (message.getType() == MessageType.ERROR) {
            return Result.fail(MessageCodec.decodePayload(message, ErrorPayload.class).message);
        }
        RoomStatusPayload status = MessageCodec.decodePayload(message, RoomStatusPayload.class);
        return Result.ok(status.roomId, status.role);
    }
}
