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

/**
 * A WS Gateway instance's live connection to one room's Game Server Shard (PLAN_split_6_services.md
 * §3.2) - the direct replacement for {@code server.game.RemoteGameSessionHandle}, which relayed the
 * exact same {@code room.<roomId>.commands}/{@code .updates} traffic from inside the monolith back
 * when the monolith still held the client sockets. Since the sockets moved here, this class does
 * the relay instead, straight to/from the local {@link MessageSink}s it's been told about (see
 * {@link #addLocalMember}) - the monolith is no longer on this path at all (see {@code Server_Design.md}
 * §5's architecture diagram: "WS Gateway <-> Game Server Shards" is a direct arrow).
 *
 * <p>Only tracks members connected to *this* Gateway instance. In a multi-instance deployment (after
 * §7's autoscaling) a room's members could be spread across several Gateway instances, each running
 * its own {@code GatewayRoomSession} for the same room, all subscribed to the same
 * {@code room.<roomId>.updates} - a targeted (non-broadcast) update for a userId this instance
 * doesn't know locally is silently ignored, which is correct (some other instance has that user).
 */
public final class GatewayRoomSession {
    private static final Duration COMMAND_TIMEOUT = Duration.ofSeconds(3);

    private final String roomId;
    private final MessageBus bus;
    private final Map<Long, MessageSink> localMembers = new ConcurrentHashMap<>();
    private final Map<Long, DisconnectWatchdog> watchdogsByUserId = new ConcurrentHashMap<>();
    private volatile boolean over = false;

    public GatewayRoomSession(String roomId, MessageBus bus) {
        this.roomId = roomId;
        this.bus = bus;
        bus.subscribe(RoomBusSubjects.updates(roomId), this::onUpdate);
    }

    public String getRoomId() { return roomId; }

    /** Registers (or replaces, on reconnect) the local socket for {@code userId} - future STATE_UPDATE/
     *  GAME_EVENT/GAME_OVER/DISCONNECT_COUNTDOWN traffic addressed to them is delivered here. */
    public void addLocalMember(long userId, MessageSink sink) {
        localMembers.put(userId, sink);
    }

    public void removeLocalMember(long userId) {
        localMembers.remove(userId);
    }

    public boolean hasLocalMember(long userId) {
        return localMembers.containsKey(userId);
    }

    /** Tells the Shard about a member who joined after allocation (almost always a spectator - white/
     *  black are already known from {@code RoomAllocatedPayload}). Fire-and-forget, same as before. */
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

    /** Called on reconnect. Stops the watchdog and clears its countdown from every client's screen -
     *  broadcast over the bus (not just to local sessions) so any *other* Gateway instance with a
     *  stake in this room clears its UI too. */
    public void cancelWatchdogIfPresent(long userId, char disconnectedColor) {
        DisconnectWatchdog watchdog = watchdogsByUserId.remove(userId);
        if (watchdog == null) return;
        watchdog.cancel();
        broadcastDisconnectCountdown(disconnectedColor, 0);
    }

    /** Used by {@link DisconnectWatchdog}'s own tick - published on the bus (not delivered to local
     *  sessions directly) for the same multi-instance reason as {@link #cancelWatchdogIfPresent}. */
    public void broadcastDisconnectCountdown(char disconnectedColor, int secondsRemaining) {
        RoutedUpdatePayload routed = new RoutedUpdatePayload(null, MessageType.DISCONNECT_COUNTDOWN,
                MessageCodec.encodeInnerPayload(new CountdownPayload(roomId, disconnectedColor, secondsRemaining)));
        bus.publish(RoomBusSubjects.updates(roomId), MessageType.ROOM_UPDATE, routed);
    }

    private void onUpdate(Message message, String replyTo) {
        RoutedUpdatePayload routed = MessageCodec.decodePayload(message, RoutedUpdatePayload.class);
        Object payload = decodeInner(routed.innerType, routed.innerPayloadJson);

        if (routed.targetUserId != null) {
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
