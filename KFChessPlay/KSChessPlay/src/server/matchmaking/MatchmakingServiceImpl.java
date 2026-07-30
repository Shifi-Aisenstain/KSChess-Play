package server.matchmaking;

import server.auth.User;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class MatchmakingServiceImpl implements MatchmakingService {
    private static final long SCAN_INTERVAL_MS = 500;
    private static final long WAIT_TIMEOUT_MS = 60_000;

    private final WaitingPlayersQueue sharedQueue;
    private final Map<Long, MatchRequest> localRequestsByUserId = new ConcurrentHashMap<>();
    private final MatchCriteria criteria;
    private final RoomAllocationClient roomAllocationClient;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(
            r -> new Thread(r, "matchmaking-scanner"));

    public MatchmakingServiceImpl(MatchCriteria criteria, RoomAllocationClient roomAllocationClient) {
        this(criteria, roomAllocationClient, new InMemoryWaitingPlayersQueue());
    }

    public MatchmakingServiceImpl(MatchCriteria criteria, RoomAllocationClient roomAllocationClient,
                                   WaitingPlayersQueue sharedQueue) {
        this.criteria = criteria;
        this.roomAllocationClient = roomAllocationClient;
        this.sharedQueue = sharedQueue;
        this.scheduler.scheduleWithFixedDelay(this::scan, SCAN_INTERVAL_MS, SCAN_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    @Override
    public void enqueue(MatchRequest request) {
        localRequestsByUserId.put(request.userId, request);
        sharedQueue.addIfAbsent(new WaitingPlayer(request.userId, request.username, request.elo, request.enqueuedAtMs));
    }

    @Override
    public void cancel(long userId) {
        localRequestsByUserId.remove(userId);
        sharedQueue.remove(userId);
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
        for (WaitingPlayer player : sharedQueue.snapshot()) {
            if (now - player.enqueuedAtMs() < WAIT_TIMEOUT_MS) continue;
            if (!sharedQueue.remove(player.userId())) continue;

            MatchRequest request = localRequestsByUserId.remove(player.userId());
            if (request != null) request.listener.onNotFound();
        }
    }

    private void pairWaitingRequests() {
        List<WaitingPlayer> snapshot = sharedQueue.snapshot();
        for (int i = 0; i < snapshot.size(); i++) {
            MatchRequest a = localRequestsByUserId.get(snapshot.get(i).userId());
            if (a == null) continue;
            for (int j = i + 1; j < snapshot.size(); j++) {
                MatchRequest b = localRequestsByUserId.get(snapshot.get(j).userId());
                if (b == null) continue;
                if (!criteria.matches(a, b)) continue;

                if (!sharedQueue.remove(a.userId) || !sharedQueue.remove(b.userId)) break;
                localRequestsByUserId.remove(a.userId);
                localRequestsByUserId.remove(b.userId);
                createMatch(a, b);
                return;
            }
        }
    }

    private void createMatch(MatchRequest first, MatchRequest second) {
        MatchRequest whiteReq = first.enqueuedAtMs <= second.enqueuedAtMs ? first : second;
        MatchRequest blackReq = whiteReq == first ? second : first;

        User white = new User(whiteReq.userId, whiteReq.username, whiteReq.elo);
        User black = new User(blackReq.userId, blackReq.username, blackReq.elo);
        String roomId = roomAllocationClient.createMatchRoom(white, black);
        if (roomId == null) {
            System.err.println("[matchmaking] room allocation failed for " + white.getUsername()
                    + " vs " + black.getUsername() + " - treating as not found");
            whiteReq.listener.onNotFound();
            blackReq.listener.onNotFound();
            return;
        }

        whiteReq.listener.onMatchFound(roomId, 'w', black.getUsername(), black.getElo());
        blackReq.listener.onMatchFound(roomId, 'b', white.getUsername(), white.getElo());
    }
}
