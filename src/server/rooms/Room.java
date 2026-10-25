package server.rooms;

import server.auth.User;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class Room {
    public enum Role { WHITE, BLACK, SPECTATOR }

    private final String id;
    private final Map<Long, Role> roles = new ConcurrentHashMap<>();
    private final Map<Long, User> users = new ConcurrentHashMap<>();
    private volatile boolean started;

    public Room(String id) {
        this.id = id;
    }

    public synchronized Role reserveRole(User user) {
        if (roles.containsKey(user.getId())) return roles.get(user.getId());
        Role role = !roles.containsValue(Role.WHITE) ? Role.WHITE
                : !roles.containsValue(Role.BLACK) ? Role.BLACK
                : Role.SPECTATOR;
        roles.put(user.getId(), role);
        users.put(user.getId(), user);
        return role;
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

    public Map<Long, Role> getRoles() { return roles; }
    public User getUser(long userId) { return users.get(userId); }
    public String getId() { return id; }
    public boolean isStarted() { return started; }
    public void markStarted() { started = true; }
}
