package server.dispatch;

import server.net.PlayerSession;
import shared.protocol.Message;

/** Command pattern: one implementation per {@link shared.protocol.MessageType}. */
public interface MessageHandler {
    void handle(PlayerSession session, Message message);
}
