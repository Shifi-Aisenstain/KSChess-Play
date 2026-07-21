package client.net;

import shared.protocol.Message;

@FunctionalInterface
public interface ServerMessageListener {
    void onMessage(Message message);
}
