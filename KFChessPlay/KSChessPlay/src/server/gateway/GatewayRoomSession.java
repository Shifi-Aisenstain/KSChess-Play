package server.gateway;

import models.Position;
import server.net.MessageSink;
import server.rooms.Room;
import shared.bus.MessageBus;
import shared.bus.RoomBusSubjects;
import shared.protocol.Message;
import shared.protocol.MessageCodec;
import shared.protocol.MessageType;
import shared.protocol.payload.BusJumpCommandPayload;
import shared.protocol.payload.BusMoveCommandPayload;
import shared.protocol.payload.BusResignPayload;
import shared.protocol.payload.BusSelectPayload;
import shared.protocol.payload.CommandAckPayload;
import shared.protocol.payload.CountdownPayload;
import shared.protocol.payload.GameEventPayload;
import shared.protocol.payload.GameOverPayload;
import shared.protocol.payload.RoomRosterPayload;
import shared.protocol.payload.RoutedCommandPayload;
import shared.protocol.payload.RoutedUpdatePayload;
import shared.protocol.payload.StateUpdatePayload;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class GatewayRoomSession {
    private static final Duration COMMAND_TIMEOUT = Duration.ofSeconds(3);

    private final String roomId;
    private final MessageBus bus;
    private final Map<Long, MessageSink> localMembers = new ConcurrentHashMap<>();
    private final Map<Long, DisconnectWatchdog> watchdogsByUserId = new ConcurrentHashMap<>();

    private final Map<Long, Character> colorByUserId = new ConcurrentHashMap<>();
    private volatile boolean over = false;

    public GatewayRoomSession(String roomId, MessageBus bus) {
        this.roomId = roomId;
        this.bus = bus;
        bus.subscribe(RoomBusSubjects.updates(roomId), this::onUpdate);
    }

    public String getRoomId() { return roomId; }

    public void addLocalMember(long userId, MessageSink sink) {
        localMembers.put(userId, sink);
    }

    public void removeLocalMember(long userId) {
        localMembers.remove(userId);
    }

    public boolean hasLocalMember(long userId) {
        return localMembers.containsKey(userId);
    }

    public char colorOf(long userId) {
        Character color = colorByUserId.get(userId);
        return color == null ? '\0' : color;
    }

    public void notifyMemberJoined(long userId, Room.Role role) {
        publishCommand(MessageType.ROOM_ROSTER, new RoomRosterPayload(userId, role.name()));
    }

    public void handleSelect(long userId, Position position) {
        boolean selected = position != null;
        publishCommand(MessageType.SELECT, new BusSelectPayload(userId, selected,
                selected ? position.getRow() : 0, selected ? position.getCol() : 0));
    }

    public boolean handleMove(long userId, Position from, Position to) {
        return requestCommandAck(MessageType.MOVE,
                new BusMoveCommandPayload(userId, from.getRow(), from.getCol(), to.getRow(), to.getCol()));
    }

    public boolean handleJump(long userId, Position at) {
        return requestCommandAck(MessageType.JUMP, new BusJumpCommandPayload(userId, at.getRow(), at.getCol()));
    }

    public void handleResign(long userId) {
        publishCommand(MessageType.RESIGN, new BusResignPayload(userId, false));
    }

    public void forceResignDueToDisconnect(long userId) {
        publishCommand(MessageType.RESIGN, new BusResignPayload(userId, true));
    }

    public boolean isOver() { return over; }

    public void registerWatchdog(long userId, DisconnectWatchdog watchdog) {
        watchdogsByUserId.put(userId, watchdog);
    }

    public void cancelWatchdogIfPresent(long userId, char disconnectedColor) {
        DisconnectWatchdog watchdog = watchdogsByUserId.remove(userId);
        if (watchdog == null) return;
        watchdog.cancel();
        broadcastDisconnectCountdown(disconnectedColor, 0);
    }

    public void broadcastDisconnectCountdown(char disconnectedColor, int secondsRemaining) {
        RoutedUpdatePayload routed = new RoutedUpdatePayload(null, MessageType.DISCONNECT_COUNTDOWN,
                MessageCodec.encodeInnerPayload(new CountdownPayload(roomId, disconnectedColor, secondsRemaining)));
        bus.publish(RoomBusSubjects.updates(roomId), MessageType.ROOM_UPDATE, routed);
    }

    private void onUpdate(Message message, String replyTo) {
        RoutedUpdatePayload routed = MessageCodec.decodePayload(message, RoutedUpdatePayload.class);
        Object payload = decodeInner(routed.innerType, routed.innerPayloadJson);

        if (routed.targetUserId != null) {
            if (routed.innerType == MessageType.STATE_UPDATE) {
                colorByUserId.put(routed.targetUserId, ((StateUpdatePayload) payload).yourColor);
            }
            MessageSink sink = localMembers.get(routed.targetUserId);
            if (sink != null) sink.send(routed.innerType, payload);
        } else {
            for (MessageSink sink : localMembers.values()) sink.send(routed.innerType, payload);
        }

        if (routed.innerType == MessageType.GAME_OVER) {
            over = true;
        }
    }

    private static Object decodeInner(MessageType innerType, String innerPayloadJson) {
        return switch (innerType) {
            case STATE_UPDATE -> MessageCodec.decodeInnerPayload(innerPayloadJson, StateUpdatePayload.class);
            case GAME_OVER -> MessageCodec.decodeInnerPayload(innerPayloadJson, GameOverPayload.class);
            case GAME_EVENT -> MessageCodec.decodeInnerPayload(innerPayloadJson, GameEventPayload.class);
            case DISCONNECT_COUNTDOWN -> MessageCodec.decodeInnerPayload(innerPayloadJson, CountdownPayload.class);
            default -> throw new IllegalArgumentException("Unexpected room.updates inner type: " + innerType);
        };
    }

    private void publishCommand(MessageType innerType, Object innerPayload) {
        bus.publish(RoomBusSubjects.commands(roomId), MessageType.ROOM_COMMAND, routedCommand(innerType, innerPayload));
    }

    private boolean requestCommandAck(MessageType innerType, Object innerPayload) {
        Optional<Message> reply = bus.request(RoomBusSubjects.commands(roomId), MessageType.ROOM_COMMAND,
                routedCommand(innerType, innerPayload), COMMAND_TIMEOUT);
        if (reply.isEmpty()) {
            System.err.println("[room " + roomId + "] no ack from Shard for " + innerType + " within "
                    + COMMAND_TIMEOUT + " - treating as rejected");
            return false;
        }
        return MessageCodec.decodePayload(reply.get(), CommandAckPayload.class).accepted;
    }

    private static RoutedCommandPayload routedCommand(MessageType innerType, Object innerPayload) {
        long userId = switch (innerType) {
            case SELECT -> ((BusSelectPayload) innerPayload).userId;
            case MOVE -> ((BusMoveCommandPayload) innerPayload).userId;
            case JUMP -> ((BusJumpCommandPayload) innerPayload).userId;
            case RESIGN -> ((BusResignPayload) innerPayload).userId;
            case ROOM_ROSTER -> ((RoomRosterPayload) innerPayload).userId;
            default -> throw new IllegalArgumentException("Unexpected room.commands inner type: " + innerType);
        };
        return new RoutedCommandPayload(userId, innerType, MessageCodec.encodeInnerPayload(innerPayload));
    }
}
