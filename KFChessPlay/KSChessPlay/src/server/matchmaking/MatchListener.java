package server.matchmaking;

/** Callback the network layer registers so it can push the outcome to the waiting client. */
public interface MatchListener {
    void onMatchFound(String roomId, char assignedColor, String opponentUsername, int opponentElo);
    void onNotFound();
}
