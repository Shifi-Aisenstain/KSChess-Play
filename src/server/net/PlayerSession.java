package server.net;

import org.java_websocket.WebSocket;
import server.auth.User;
import server.logging.ServerActivityLogger;
import shared.protocol.MessageCodec;
import shared.protocol.MessageType;

public final class PlayerSession implements MessageSink {
    private final WebSocket connection;
    private final ServerActivityLogger activityLogger;
    private volatile User user;
    private volatile String currentRoomId;

    public PlayerSession(WebSocket connection, ServerActivityLogger activityLogger) {
        this.connection = connection;
        this.activityLogger = activityLogger;
    }

    @Override
    public void send(MessageType type, Object payload) {
        if (!connection.isOpen()) return;
        String json = MessageCodec.encode(type, payload);
        connection.send(json);
        activityLogger.logTraffic("OUT", user == null ? null : user.getId(), type.name(), json);
    }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public String getCurrentRoomId() { return currentRoomId; }
    public void setCurrentRoomId(String roomId) { this.currentRoomId = roomId; }

    public WebSocket getConnection() { return connection; }
}
