package server.shard;

import models.Position;
import server.auth.User;
import server.game.GameSession;
import server.history.GameHistoryRecorder;
import server.rating.EloWriter;
import server.rating.RatingService;
import server.rooms.Room;
import shared.bus.MessageBus;
import shared.bus.RoomBusSubjects;
import shared.eventbus.EventBus;
import shared.metrics.MetricsRegistry;
import shared.protocol.Message;
import shared.protocol.MessageCodec;
import shared.protocol.MessageType;
import shared.protocol.payload.BusJumpCommandPayload;
import shared.protocol.payload.BusMoveCommandPayload;
import shared.protocol.payload.BusResignPayload;
import shared.protocol.payload.BusSelectPayload;
import shared.protocol.payload.CommandAckPayload;
import shared.protocol.payload.RoomAllocatedPayload;
import shared.protocol.payload.RoomRosterPayload;
import shared.protocol.payload.RoutedCommandPayload;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class GameShardRuntime {
    private final MessageBus bus;
    private final EventBus eventBus;
    private final RatingService ratingService;
    private final EloWriter eloWriter;
    private final GameHistoryRecorder historyRecorder;
    private final String shardId;
    private final MetricsRegistry metricsRegistry;
    private final Map<String, GameSession> activeSessions = new ConcurrentHashMap<>();

    private final Set<String> allocatedRoomIds = ConcurrentHashMap.newKeySet();

    public GameShardRuntime(MessageBus bus, EventBus eventBus, RatingService ratingService, EloWriter eloWriter,
                             GameHistoryRecorder historyRecorder, String shardId, MetricsRegistry metricsRegistry) {
        this.bus = bus;
        this.eventBus = eventBus;
        this.ratingService = ratingService;
        this.eloWriter = eloWriter;
        this.historyRecorder = historyRecorder;
        this.shardId = shardId;
        this.metricsRegistry = metricsRegistry;
    }

    public void start() {
        new ShardGameEventRelay(eventBus, bus);
        new ShardGameHistoryRelay(eventBus, historyRecorder);
        bus.subscribe(RoomBusSubjects.ROOM_ALLOCATED, (message, replyTo) -> onRoomAllocated(message));
    }

    private void onRoomAllocated(Message message) {
        RoomAllocatedPayload allocation = MessageCodec.decodePayload(message, RoomAllocatedPayload.class);
        if (!shardId.equals(allocation.shardId)) return;
        if (!allocatedRoomIds.add(allocation.roomId)) return;

        User white = new User(allocation.whiteId, allocation.whiteUsername, allocation.whiteElo);
        User black = new User(allocation.blackId, allocation.blackUsername, allocation.blackElo);
        GameSession session = new GameSession(allocation.roomId, new BusUpdateSink(bus, allocation.roomId),
                white, black, eventBus, ratingService, eloWriter, historyRecorder);
        activeSessions.put(allocation.roomId, session);
        metricsRegistry.incrementCounter("shard_rooms_allocated_total", "Total rooms allocated to this shard");
        metricsRegistry.setGauge("shard_active_games", "Games currently running on this shard", activeSessions.size());

        bus.subscribe(RoomBusSubjects.commands(allocation.roomId),
                (message2, replyTo) -> onCommand(session, message2, replyTo));
        session.start();
    }

    private void onCommand(GameSession session, Message message, String replyTo) {
        RoutedCommandPayload command = MessageCodec.decodePayload(message, RoutedCommandPayload.class);
        metricsRegistry.incrementCounter("shard_commands_total", "Total gameplay commands processed by this shard");
        switch (command.innerType) {
            case SELECT -> {
                BusSelectPayload p = MessageCodec.decodeInnerPayload(command.innerPayloadJson, BusSelectPayload.class);
                session.handleSelect(command.userId, p.selected ? new Position(p.row, p.col) : null);
            }
            case MOVE -> {
                BusMoveCommandPayload p = MessageCodec.decodeInnerPayload(command.innerPayloadJson, BusMoveCommandPayload.class);
                boolean accepted = session.handleMove(command.userId,
                        new Position(p.fromRow, p.fromCol), new Position(p.toRow, p.toCol));
                replyAck(replyTo, accepted);
            }
            case JUMP -> {
                BusJumpCommandPayload p = MessageCodec.decodeInnerPayload(command.innerPayloadJson, BusJumpCommandPayload.class);
                boolean accepted = session.handleJump(command.userId, new Position(p.row, p.col));
                replyAck(replyTo, accepted);
            }
            case RESIGN -> {
                BusResignPayload p = MessageCodec.decodeInnerPayload(command.innerPayloadJson, BusResignPayload.class);
                if (p.dueToDisconnect) session.forceResignDueToDisconnect(command.userId);
                else session.handleResign(command.userId);
            }
            case ROOM_ROSTER -> {
                RoomRosterPayload p = MessageCodec.decodeInnerPayload(command.innerPayloadJson, RoomRosterPayload.class);
                session.onMemberJoined(p.userId, Room.Role.valueOf(p.role));
            }
            default -> System.err.println("[shard] unexpected room.commands inner type: " + command.innerType);
        }
    }

    private void replyAck(String replyTo, boolean accepted) {
        if (replyTo == null) return;
        bus.reply(replyTo, MessageType.COMMAND_ACK, new CommandAckPayload(accepted));
    }
}
