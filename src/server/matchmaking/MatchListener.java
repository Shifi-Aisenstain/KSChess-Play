package server.matchmaking;

public interface MatchListener {
    void onMatchFound(String roomId, char assignedColor, String opponentUsername, int opponentElo);
    void onNotFound();
}
