package server.gateway;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

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
