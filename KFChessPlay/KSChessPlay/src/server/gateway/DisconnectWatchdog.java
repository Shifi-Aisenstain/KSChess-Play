package server.gateway;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Spec: "If player disconnected - auto-resign after 20 sec. Make a 'count
 * down' on the screen." One watchdog instance is created per disconnect
 * event; if the same socket comes back before the countdown reaches zero,
 * whoever detected the reconnect calls {@link #cancel()}.
 *
 * <p>Moved here from {@code server.game.DisconnectWatchdog} as part of PLAN_split_6_services.md
 * §3.2: it's a per-socket concern, so it follows the sockets to the WS Gateway. It no longer talks
 * to a {@code Room} or a game-engine handle directly - both the countdown broadcast and the eventual
 * auto-resign now go through {@link GatewayRoomSession}, which is what actually reaches the room
 * (over the bus) and the Shard (also over the bus) respectively.
 */
public final class DisconnectWatchdog {
    private static final int TOTAL_SECONDS = 20;

    private final GatewayRoomSession session;
    private final long disconnectedUserId;
    private final char disconnectedColor;
    private final AtomicInteger remaining = new AtomicInteger(TOTAL_SECONDS);
    private ScheduledExecutorService scheduler;

    public DisconnectWatchdog(GatewayRoomSession session, long disconnectedUserId, char disconnectedColor) {
        this.session = session;
        this.disconnectedUserId = disconnectedUserId;
        this.disconnectedColor = disconnectedColor;
    }

    public void start() {
        scheduler = Executors.newSingleThreadScheduledExecutor(
                r -> new Thread(r, "disconnect-watchdog-" + session.getRoomId()));
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
        session.broadcastDisconnectCountdown(disconnectedColor, Math.max(secondsLeft, 0));

        if (secondsLeft <= 0) {
            session.forceResignDueToDisconnect(disconnectedUserId);
            cancel();
        }
    }
}
