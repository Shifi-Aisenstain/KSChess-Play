package server.shard;

import server.game.GameUpdateSink;
import shared.bus.MessageBus;
import shared.bus.RoomBusSubjects;
import shared.protocol.MessageCodec;
import shared.protocol.MessageType;
import shared.protocol.payload.RoutedUpdatePayload;

public final class BusUpdateSink implements GameUpdateSink {
    private final MessageBus bus;
    private final String roomId;

    public BusUpdateSink(MessageBus bus, String roomId) {
        this.bus = bus;
        this.roomId = roomId;
    }

    @Override
    public void sendTo(long userId, MessageType type, Object payload) {
        publish(userId, type, payload);
    }

    @Override
    public void broadcastToAll(MessageType type, Object payload) {
        publish(null, type, payload);
    }

    private void publish(Long targetUserId, MessageType type, Object payload) {
        RoutedUpdatePayload routed = new RoutedUpdatePayload(targetUserId, type, MessageCodec.encodeInnerPayload(payload));
        bus.publish(RoomBusSubjects.updates(roomId), MessageType.ROOM_UPDATE, routed);
    }
}
