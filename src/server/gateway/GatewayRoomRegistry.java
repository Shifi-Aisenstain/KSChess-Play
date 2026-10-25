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

        GatewayRoomSession session = sessions.computeIfAbsent(allocation.roomId, id -> new GatewayRoomSession(id, bus));
        List<PendingMember> waiting = pendingByRoomId.remove(allocation.roomId);
        if (waiting == null) return;
        for (PendingMember member : waiting) session.addLocalMember(member.userId(), member.sink());
    }

    public Optional<GatewayRoomSession> registerPendingMember(String roomId, long userId, MessageSink sink) {
        GatewayRoomSession existing = sessions.get(roomId);
        if (existing != null) {
            existing.addLocalMember(userId, sink);
            return Optional.of(existing);
        }
        pendingByRoomId.computeIfAbsent(roomId, id -> new CopyOnWriteArrayList<>()).add(new PendingMember(userId, sink));
        return Optional.empty();
    }

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
