package server.dispatch;

import server.net.PlayerSession;
import shared.protocol.Message;
import shared.protocol.MessageType;
import shared.protocol.payload.ErrorPayload;

import java.util.EnumMap;
import java.util.Map;

/**
 * Routes an incoming {@link Message} to whichever {@link MessageHandler} was
 * registered for its type (Command pattern). This is the only class in the
 * server that needs to know the full set of message types exists - handlers
 * themselves stay single-purpose and testable in isolation.
 */
public final class MessageDispatcher {
    private final Map<MessageType, MessageHandler> handlers = new EnumMap<>(MessageType.class);

    public void register(MessageType type, MessageHandler handler) {
        handlers.put(type, handler);
    }

    public void dispatch(PlayerSession session, Message message) {
        MessageHandler handler = handlers.get(message.getType());
        if (handler == null) {
            session.send(MessageType.ERROR, new ErrorPayload("Unsupported message type: " + message.getType()));
            return;
        }
        try {
            handler.handle(session, message);
        } catch (RuntimeException e) {
            session.send(MessageType.ERROR, new ErrorPayload("Server error: " + e.getMessage()));
        }
    }
}
