package server.game;

import shared.protocol.MessageType;

/**
 * Where a {@link GameSession} sends its outgoing traffic - extracted so {@code GameSession} no
 * longer depends on {@code server.rooms.Room} directly (PLAN_split_6_services.md §3.1: "publishes
 * updates to room.&lt;roomId&gt;.updates instead of Room.sendTo directly").
 *
 * <p>Two implementations: {@code server.rooms.RoomUpdateSink} wraps a live {@code Room} and writes
 * straight to its members' sockets - used wherever a {@link GameSession} still runs in the same
 * process as its clients (tests, and conceptually "before the split"). {@code server.shard.BusUpdateSink}
 * wraps a {@code shared.bus.MessageBus} and publishes to {@code room.<roomId>.updates} instead -
 * used by the real {@code GameServerShardMain}, where the actual sockets live in a different process.
 */
public interface GameUpdateSink {
    void sendTo(long userId, MessageType type, Object payload);

    void broadcastToAll(MessageType type, Object payload);
}
