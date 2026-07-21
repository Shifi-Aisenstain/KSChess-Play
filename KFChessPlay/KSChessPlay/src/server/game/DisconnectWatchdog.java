package server.game;

import server.rooms.Room;
import shared.protocol.MessageType;
import shared.protocol.payload.CountdownPayload;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Spec: "If player disconnected - auto-resign after 20 sec. Make a 'count
 * down' on the screen." One watchdog instance is created per disconnect
 * event; if the same socket comes back before the countdown reaches zero,
 * whoever detected the reconnect calls {@link #cancel()}.
 */
public final class DisconnectWatchdog {
    private static final int TOTAL_SECONDS = 20;

    private final Room room;
    private final GameSession session;
    private final long disconnectedUserId;
    private final char disconnectedColor;
    private final AtomicInteger remaining = new AtomicInteger(TOTAL_SECONDS);
    private ScheduledExecutorService scheduler;

    public DisconnectWatchdog(Room room, GameSession session, long disconnectedUserId, char disconnectedColor) {
        this.room = room;
        this.session = session;
        this.disconnectedUserId = disconnectedUserId;
        this.disconnectedColor = disconnectedColor;
    }

    public void start() {
        scheduler = Executors.newSingleThreadScheduledExecutor(
                r -> new Thread(r, "disconnect-watchdog-" + room.getId()));
        scheduler.scheduleAtFixedRate(this::tick, 0, 1, TimeUnit.SECONDS);
    }

    public void cancel() {
        if (scheduler != null) scheduler.shutdownNow();
    }

    private void tick() {
        if (session.isOver()) {
            cancel();
            return;
        }
        int secondsLeft = remaining.getAndDecrement();
        room.broadcastToAll(MessageType.DISCONNECT_COUNTDOWN,
                new CountdownPayload(room.getId(), disconnectedColor, Math.max(secondsLeft, 0)));

        if (secondsLeft <= 0) {
            session.forceResignDueToDisconnect(disconnectedUserId);
            cancel();
        }
    }
}
