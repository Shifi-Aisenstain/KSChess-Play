package shared.bus;

import shared.protocol.Message;

@FunctionalInterface
public interface BusMessageHandler {

    void onMessage(Message message, String replySubject);
}
