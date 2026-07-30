package server.matchmaking;

/**
 * Pure, serializable snapshot of one queued matchmaking request (Server_Design.md step 4) - what
 * actually needs to be shared across Matchmaker replicas via a {@link WaitingPlayersQueue}.
 * Deliberately carries no {@link MatchListener}, unlike {@link MatchRequest}: a Java callback
 * reference is only meaningful inside the one process/heap that created it, so it can never be part
 * of state shared over Redis.
 */
public record WaitingPlayer(long userId, String username, int elo, long enqueuedAtMs) { }
