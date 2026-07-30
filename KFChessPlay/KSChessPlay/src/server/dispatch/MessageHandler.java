package server.dispatch;

import server.net.PlayerSession;
import shared.protocol.Message;

public interface MessageHandler {
    void handle(PlayerSession session, Message message);
}
