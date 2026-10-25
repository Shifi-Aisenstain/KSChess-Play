package server.net;

import shared.protocol.MessageType;

public interface MessageSink {
    void send(MessageType type, Object payload);
}
