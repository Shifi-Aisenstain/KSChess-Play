package server.net;

import org.java_websocket.WebSocket;
import server.logging.ServerActivityLogger;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Identity-keyed map from a live socket to its {@link PlayerSession}. */
public final class SessionRegistry {
    private final Map<WebSocket, PlayerSession> sessions = new ConcurrentHashMap<>();
    private final ServerActivityLogger activityLogger;

    public SessionRegistry(ServerActivityLogger activityLogger) {
        this.activityLogger = activityLogger;
    }

    public PlayerSession getOrCreate(WebSocket connection) {
        return sessions.computeIfAbsent(connection, c -> new PlayerSession(c, activityLogger));
    }

    public PlayerSession get(WebSocket connection) {
        return sessions.get(connection);
    }

    public void remove(WebSocket connection) {
        sessions.remove(connection);
    }
}
