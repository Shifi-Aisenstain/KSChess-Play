package server.game;

import shared.protocol.MessageType;

public interface GameUpdateSink {
    void sendTo(long userId, MessageType type, Object payload);

    void broadcastToAll(MessageType type, Object payload);
}
