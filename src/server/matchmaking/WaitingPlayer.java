package server.matchmaking;

public record WaitingPlayer(long userId, String username, int elo, long enqueuedAtMs) { }
