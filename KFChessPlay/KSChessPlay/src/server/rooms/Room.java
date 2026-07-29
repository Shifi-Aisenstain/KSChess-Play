package server.rooms;

import server.auth.User;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A room is the meeting point of up to two players and any number of
 * spectators (spec 1.d). It owns member bookkeeping only - who has which role.
 *
 * <p>Until PLAN_split_6_services.md §3.2, this class also owned a live socket per member
 * ({@code sinks}) and the attached {@code GameSessionHandle}, because the monolith held both the
 * room bookkeeping and the client sockets in the same process. Since §3.2 (the WS Gateway
 * extraction), the sockets live in a separate process entirely, and gameplay traffic flows directly
 * between the WS Gateway and the Game Server Shard over the bus (see
 * {@code server.gateway.GatewayRoomSession}) - the monolith (still hosting this class, per §3.2's
 * scope) is never on that path anymore, so there is nothing here left to send messages *to*.
 * {@link #started} is the one bit of state {@code RoomManager} still needs: whether this room's game
 * has already been allocated to a Shard, so a second reconnect-as-BLACK doesn't re-allocate it.
 */
public final class Room {
    public enum Role { WHITE, BLACK, SPECTATOR }

    private final String id;
    private final Map<Long, Role> roles = new ConcurrentHashMap<>();
    private final Map<Long, User> users = new ConcurrentHashMap<>();
    private volatile boolean started;

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
