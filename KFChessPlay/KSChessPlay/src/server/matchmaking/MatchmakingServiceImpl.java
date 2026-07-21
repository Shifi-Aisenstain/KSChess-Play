package server.matchmaking;

import server.auth.User;
import server.rooms.Room;
import server.rooms.RoomManager;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Background matchmaking queue.
 * <p>Every {@link #SCAN_INTERVAL_MS} it scans the waiting list for a pair
 * satisfying {@link MatchCriteria} (Strategy pattern - see
 * {@link EloRangeMatchCriteria}), and separately expires anyone who has been
 * waiting longer than {@link #WAIT_TIMEOUT_MS} (spec: 1 minute) with a
 * "not found" notice.
 */
public final class MatchmakingServiceImpl implements MatchmakingService {
    private static final long SCAN_INTERVAL_MS = 500;
    private static final long WAIT_TIMEOUT_MS = 60_000;

    private final List<MatchRequest> waiting = new CopyOnWriteArrayList<>();
    private final MatchCriteria criteria;
    private final RoomManager roomManager;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(
            r -> new Thread(r, "matchmaking-scanner"));

    public MatchmakingServiceImpl(MatchCriteria criteria, RoomManager roomManager) {
        this.criteria = criteria;
        this.roomManager = roomManager;
        this.scheduler.scheduleWithFixedDelay(this::scan, SCAN_INTERVAL_MS, SCAN_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    @Override
    public void enqueue(MatchRequest request) {
        waiting.add(request);
    }

    @Override
    public void cancel(long userId) {
        waiting.removeIf(r -> r.userId == userId);
    }

    @Override
    public void shutdown() {
        scheduler.shutdownNow();
    }

    private void scan() {
        try {
            expireStaleRequests();
            pairWaitingRequests();
        } catch (RuntimeException e) {
            System.err.println("[matchmaking] scan failed: " + e.getMessage());
        }
    }

    private void expireStaleRequests() {
        long now = System.currentTimeMillis();
        for (MatchRequest request : waiting) {
            if (now - request.enqueuedAtMs >= WAIT_TIMEOUT_MS) {
                waiting.remove(request);
                request.listener.onNotFound();
            }
        }
    }

    private void pairWaitingRequests() {
        for (int i = 0; i < waiting.size(); i++) {
            MatchRequest a = waiting.get(i);
            for (int j = i + 1; j < waiting.size(); j++) {
                MatchRequest b = waiting.get(j);
                if (!criteria.matches(a, b)) continue;

                if (!waiting.remove(a) || !waiting.remove(b)) break; // lost a race, bail out safely
                createMatch(a, b);
                return; // restart scan cleanly next tick rather than mutate further mid-iteration
            }
        }
    }

    /** Whoever queued first plays White, mirroring the room feature's "creator" convention. */
    private void createMatch(MatchRequest first, MatchRequest second) {
        MatchRequest whiteReq = first.enqueuedAtMs <= second.enqueuedAtMs ? first : second;
        MatchRequest blackReq = whiteReq == first ? second : first;

        User white = new User(whiteReq.userId, whiteReq.username, whiteReq.elo);
        User black = new User(blackReq.userId, blackReq.username, blackReq.elo);
        Room room = roomManager.createRoomForMatch(white, black);

        whiteReq.listener.onMatchFound(room.getId(), 'w', black.getUsername(), black.getElo());
        blackReq.listener.onMatchFound(room.getId(), 'b', white.getUsername(), white.getElo());
    }
}
