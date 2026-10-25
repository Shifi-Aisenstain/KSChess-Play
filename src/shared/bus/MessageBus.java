package shared.bus;

import shared.protocol.Message;
import shared.protocol.MessageType;

import java.time.Duration;
import java.util.Optional;

public interface MessageBus extends AutoCloseable {

    void publish(String subject, MessageType type, Object payload);

    Subscription subscribe(String subject, BusMessageHandler handler);

    Optional<Message> request(String subject, MessageType type, Object payload, Duration timeout);

    void reply(String replySubject, MessageType type, Object payload);

    @Override
    void close();

    interface Subscription {
        void unsubscribe();
    }
}
