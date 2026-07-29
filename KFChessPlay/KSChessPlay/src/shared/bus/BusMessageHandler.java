package shared.bus;

import shared.protocol.Message;

/** Callback for messages received via {@link MessageBus#subscribe}. */
@FunctionalInterface
public interface BusMessageHandler {
    /**
     * @param message      the decoded envelope.
     * @param replySubject non-null only when the sender used {@link MessageBus#request} and is
     *                     waiting for an answer - pass it straight to {@link MessageBus#reply} to
     *                     respond. Null for fire-and-forget {@link MessageBus#publish} traffic.
     */
    void onMessage(Message message, String replySubject);
}
