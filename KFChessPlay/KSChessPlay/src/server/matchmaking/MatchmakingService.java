package server.matchmaking;

public interface MatchmakingService {
    void enqueue(MatchRequest request);
    void cancel(long userId);
    void shutdown();
}
