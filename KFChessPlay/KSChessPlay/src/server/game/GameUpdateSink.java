package server.game;

import shared.protocol.MessageType;

/**
 * Where a {@link GameSession} sends its outgoing traffic - extracted so {@code GameSession} no
 * longer depends on {@code server.rooms.Room} directly (PLAN_split_6_services.md §3.1: "publishes
 * updates to room.&lt;roomId&gt;.updates instead of Room.sendTo directly").
 *
 * <p>Since PLAN_split_6_services.md §3.2 (the WS Gateway extraction), there is exactly one production
 * implementation: {@code server.shard.BusUpdateSink}, which wraps a {@code shared.bus.MessageBus} and
 * publishes to {@code room.<roomId>.updates} - the sockets live in a different process (the WS
 * Gateway) from wherever a {@link GameSession} actually runs (the Game Server Shard), so there is
 * nothing left in this process to write to directly. Test code that wants a {@link GameSession} in
 * the same process as its assertions supplies a small test double instead (see
 * {@code GameSessionConcurrencyTest}).
 */
public interface GameUpdateSink {
    void sendTo(long userId, MessageType type, Object payload);

    void broadcastToAll(MessageType type, Object payload);
}
