package server.rooms;

import server.auth.User;
import server.game.GameSession;
import server.net.MessageSink;
import shared.protocol.MessageType;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A room is the meeting point of up to two players and any number of
 * spectators (spec 1.d). It owns member bookkeeping and broadcast; the
 * actual chess simulation lives in the attached {@link GameSession} so a
 * room without two players yet (waiting for a Join) simply has none.
 */
public final class Room {
    public enum Role { WHITE, BLACK, SPECTATOR }

    private final String id;
    private final Map<Long, MessageSink> sinks = new ConcurrentHashMap<>();
    private final Map<Long, Role> roles = new LinkedHashMap<>();
    private final Map<Long, User> users = new ConcurrentHashMap<>();
    private volatile GameSession gameSession;

    public Room(String id) {
        this.id = id;
    }

    /** Idempotent: a user who already has a role in this room keeps it. */
    public synchronized Role reserveRole(User user) {
        if (roles.containsKey(user.getId())) return roles.get(user.getId());
        Role role = !roles.containsValue(Role.WHITE) ? Role.WHITE
                : !roles.containsValue(Role.BLACK) ? Role.BLACK
                : Role.SPECTATOR;
        roles.put(user.getId(), role);
        users.put(user.getId(), user);
        return role;
    }

    public void attachSink(long userId, MessageSink sink) {
        sinks.put(userId, sink);
    }

    public void detachMember(long userId) {
        sinks.remove(userId);
    }

    public boolean isFull() {
        return roles.containsValue(Role.WHITE) && roles.containsValue(Role.BLACK);
    }

    public User getUser(char color) {
        Role target = color == 'w' ? Role.WHITE : Role.BLACK;
        return roles.entrySet().stream()
                .filter(e -> e.getValue() == target)
                .map(e -> users.get(e.getKey()))
                .findFirst()
                .orElse(null);
    }

    public void broadcastToAll(MessageType type, Object payload) {
        for (MessageSink sink : sinks.values()) sink.send(type, payload);
    }

    public void sendTo(long userId, MessageType type, Object payload) {
        MessageSink sink = sinks.get(userId);
        if (sink != null) sink.send(type, payload);
    }

    public Map<Long, Role> getRoles() { return roles; }
    public User getUser(long userId) { return users.get(userId); }
    public String getId() { return id; }
    public GameSession getGameSession() { return gameSession; }
    public void setGameSession(GameSession gameSession) { this.gameSession = gameSession; }
}
