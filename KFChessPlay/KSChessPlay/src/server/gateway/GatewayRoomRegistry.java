package server.gateway;

import server.net.MessageSink;
import shared.bus.MessageBus;
import shared.bus.RoomBusSubjects;
import shared.protocol.Message;
import shared.protocol.MessageCodec;
import shared.protocol.payload.RoomAllocatedPayload;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * One WS Gateway instance's view of every room it has a local stake in (PLAN_split_6_services.md
 * §3.2). Subscribes once, globally, to {@code room.allocated} - the same subject the Shard listens
 * on (§3.1) - to learn when a room it cares about is ready to talk to directly.
 *
 * <p>A room reaches this registry two different ways, and the ordering between them isn't
 * guaranteed (real NATS gives no cross-subject delivery-order guarantee, only same-subject FIFO per
 * publisher):
 * <ol>
 *   <li>{@link #registerPendingMember} - a locally-connected user was just confirmed into a room
 *       (ROOM_CREATE/ROOM_JOIN reply, or MATCH_FOUND), possibly before the room has a running game
 *       yet (e.g. the creator, waiting for a second player).</li>
 *   <li>{@link #onRoomAllocated} - the monolith (standing in for the Game Allocator) published
 *       {@code room.allocated} for this room - only from that point on is it actually safe to publish
 *       {@code room.<roomId>.commands} (the Shard's own subscription is guaranteed to exist by then).</li>
 * </ol>
 * Whichever happens second is what actually creates the room's one {@link GatewayRoomSession} and/or
 * attaches the waiting local member(s) to it - see the two methods' own comments.
 */
public final class GatewayRoomRegistry {
    private final MessageBus bus;
    private final Map<String, GatewayRoomSession> sessions = new ConcurrentHashMap<>();
    private final Map<String, List<PendingMember>> pendingByRoomId = new ConcurrentHashMap<>();

    public GatewayRoomRegistry(MessageBus bus) {
        this.bus = bus;
        bus.subscribe(RoomBusSubjects.ROOM_ALLOCATED, (message, replyTo) -> onRoomAllocated(message));
    }

    private void onRoomAllocated(Message message) {
        RoomAllocatedPayload allocation = MessageCodec.decodePayload(message, RoomAllocatedPayload.class);
        // Always materialize the session here, even if nothing is pending yet locally (this Gateway
        // instance might not have any local member in this room at all, which is fine - the
        // GatewayRoomSession just sits there with zero local members and ignores all traffic; or a
        // local member could still be about to call registerPendingMember for this exact room a
        // moment from now, e.g. a slow matchmaking.results delivery under real NATS - see class
        // javadoc. Either way "the session already exists" is what registerPendingMember checks.
        GatewayRoomSession session = sessions.computeIfAbsent(allocation.roomId, id -> new GatewayRoomSession(id, bus));
        List<PendingMember> waiting = pendingByRoomId.remove(allocation.roomId);
        if (waiting == null) return;
        for (PendingMember member : waiting) session.addLocalMember(member.userId(), member.sink());
    }

    /** Called once a locally-connected user is confirmed into a room. If the room's
     *  {@link GatewayRoomSession} already exists (room.allocated already arrived - the common case
     *  for a spectator joining, or a second player joining an already-started room), attaches
     *  immediately and returns it (so the caller can, e.g., tell the Shard about a spectator via
     *  {@link GatewayRoomSession#notifyMemberJoined} - white/black don't need that, they're already
     *  known to the Shard from the allocation payload itself); otherwise queues until
     *  {@link #onRoomAllocated} promotes it and returns empty. */
    public Optional<GatewayRoomSession> registerPendingMember(String roomId, long userId, MessageSink sink) {
        GatewayRoomSession existing = sessions.get(roomId);
        if (existing != null) {
            existing.addLocalMember(userId, sink);
            return Optional.of(existing);
        }
        pendingByRoomId.computeIfAbsent(roomId, id -> new CopyOnWriteArrayList<>()).add(new PendingMember(userId, sink));
        return Optional.empty();
    }

    /**
     * Used for reconnect ({@code LoginHandler}): the monolith's active-room lookup only ever returns
     * rooms with an in-progress game, so room.allocated has, by definition, already happened for it
     * (possibly on a different Gateway instance, or before this one's process even started) - no
     * point waiting for an event that already came and went. Builds/reuses the session and attaches
     * immediately.
     */
    public GatewayRoomSession attachToAlreadyRunningRoom(String roomId, long userId, MessageSink sink) {
        GatewayRoomSession session = sessions.computeIfAbsent(roomId, id -> new GatewayRoomSession(id, bus));
        session.addLocalMember(userId, sink);
        return session;
    }

    public Optional<GatewayRoomSession> get(String roomId) {
        return Optional.ofNullable(sessions.get(roomId));
    }

    private record PendingMember(long userId, MessageSink sink) { }
}
