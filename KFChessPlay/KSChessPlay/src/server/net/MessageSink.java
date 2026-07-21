package server.net;

import shared.protocol.MessageType;

/**
 * Everything upstream of the raw socket (rooms, game sessions, matchmaking)
 * talks to a connected client through this interface only. That's what lets
 * {@code server.rooms}/{@code server.game}/{@code server.matchmaking} stay
 * ignorant of WebSocket/Gson entirely - {@code PlayerSession} is the single
 * adapter that implements it in terms of a real socket connection.
 */
public interface MessageSink {
    void send(MessageType type, Object payload);
}
