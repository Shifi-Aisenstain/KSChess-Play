package server.dispatch.handlers;

import server.dispatch.MessageHandler;
import server.net.PlayerSession;
import shared.protocol.Message;
import shared.protocol.MessageType;
import shared.protocol.payload.EmptyPayload;

public final class PingHandler implements MessageHandler {
    @Override
    public void handle(PlayerSession session, Message message) {
        session.send(MessageType.PONG, EmptyPayload.INSTANCE);
    }
}
