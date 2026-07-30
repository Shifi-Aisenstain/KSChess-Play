package server.shard;

import models.Position;
import server.auth.User;
import server.game.GameSession;
import server.rating.EloWriter;
import server.rating.RatingService;
import server.rooms.Room;
import shared.bus.MessageBus;
import shared.bus.RoomBusSubjects;
import shared.eventbus.EventBus;
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

/**
 * The actual "run games" logic of a Game Server Shard (PLAN_split_6_services.md §3.1), factored out
 * of {@link GameServerShardMain} so it can be exercised directly against an
 * {@code shared.bus.InMemoryMessageBus} in tests - exactly the way {@code shared.bus.NatsMessageBus}
 * itself is excluded from unit tests in favor of the in-memory double (see {@code MessageBusTest}'s
 * javadoc). {@link GameServerShardMain}'s {@code main()} is the only thing that's different in
 * production: it wires this class to a real {@code NatsMessageBus}.
 *
 * <p>Listens on the global {@link RoomBusSubjects#ROOM_ALLOCATED} subject for new rooms, and per-room
 * on {@link RoomBusSubjects#commands} for SELECT/MOVE/JUMP/RESIGN/ROOM_ROSTER traffic - see that
 * class's javadoc for the full subject table. One {@link GameSession} per allocated room, exactly
 * like {@code RoomManager} used to hold one per room in-process before the split.
 *
 * <p>Since PLAN_split_6_services.md §3.4, {@code room.allocated} is broadcast to every Shard
 * instance rather than addressed to just one (there's no per-shard subject), so each allocation
 * carries a {@link RoomAllocatedPayload#shardId} and every Shard ignores allocations meant for one
 * of its siblings - see {@link #shardId} and {@code server.allocator.ShardSelector}, which is what
 * actually decides that field's value.
 */
public final class GameShardRuntime {
    private final MessageBus bus;
    private final EventBus eventBus;
    private final RatingService ratingService;
    private final EloWriter eloWriter;
    private final String shardId;
    private final Map<String, GameSession> activeSessions = new ConcurrentHashMap<>();
    /** Guards against double-allocating the same room (e.g. a duplicate/retried {@code room.allocated})
     *  - checked-and-claimed atomically via {@link Set#add}, since {@link ConcurrentHashMap} can't
     *  hold a null placeholder value the way {@code putIfAbsent} would need before the real
     *  {@link GameSession} is built. */
    private final Set<String> allocatedRoomIds = ConcurrentHashMap.newKeySet();

    public GameShardRuntime(MessageBus bus, EventBus eventBus, RatingService ratingService,
                             EloWriter eloWriter, String shardId) {
        this.bus = bus;
        this.eventBus = eventBus;
        this.ratingService = ratingService;
        this.eloWriter = eloWriter;
        this.shardId = shardId;
    }

    /** Starts listening for newly allocated rooms. Idempotent w.r.t. a given room id - a duplicate
     *  {@code room.allocated} for a room this Shard already runs is ignored. */
    public void start() {
        new ShardGameEventRelay(eventBus, bus);
        bus.subscribe(RoomBusSubjects.ROOM_ALLOCATED, (message, replyTo) -> onRoomAllocated(message));
    }

    private void onRoomAllocated(Message message) {
        RoomAllocatedPayload allocation = MessageCodec.decodePayload(message, RoomAllocatedPayload.class);
        if (!shardId.equals(allocation.shardId)) return; // allocated to a sibling Shard, not us
        if (!allocatedRoomIds.add(allocation.roomId)) return; // already running this room

        User white = new User(allocation.whiteId, allocation.whiteUsername, allocation.whiteElo);
        User black = new User(allocation.blackId, allocation.blackUsername, allocation.blackElo);
        GameSession session = new GameSession(allocation.roomId, new BusUpdateSink(bus, allocation.roomId),
                white, black, eventBus, ratingService, eloWriter);
        activeSessions.put(allocation.roomId, session);

        bus.subscribe(RoomBusSubjects.commands(allocation.roomId),
                (message2, replyTo) -> onCommand(session, message2, replyTo));
        session.start();
    }

    private void onCommand(GameSession session, Message message, String replyTo) {
        RoutedCommandPayload command = MessageCodec.decodePayload(message, RoutedCommandPayload.class);
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
        if (replyTo == null) return; // shouldn't happen for MOVE/JUMP (always sent via request/reply), but never block a fire-and-forget caller
        bus.reply(replyTo, MessageType.COMMAND_ACK, new CommandAckPayload(accepted));
    }
}
