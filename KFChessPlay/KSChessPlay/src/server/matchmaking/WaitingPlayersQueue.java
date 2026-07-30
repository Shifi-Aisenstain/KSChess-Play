package server.matchmaking;

import java.util.List;

/**
 * Abstraction over where the matchmaking queue's shared data actually lives (Server_Design.md step
 * 4: "Redis for the matchmaking queue... once there's more than one process"). Every method must be
 * safe under concurrent calls racing on the same backing store.
 *
 * <p><b>Scope note:</b> only the queue's data is shared this way - result delivery (who gets notified
 * when a match is found) still happens entirely over the existing NATS {@code matchmaking.results}
 * broadcast (see {@code shared.bus.RoomBusSubjects#MATCHMAKING_RESULTS}), which every WS Gateway
 * instance already listens to regardless of which Matchmaker replica produced the result - so nothing
 * changed there. What this interface does <em>not</em> attempt to solve: if {@link MatchmakingServiceImpl}
 * itself were ever scaled to multiple replicas, every replica's own {@code MatchmakerRuntime}
 * independently builds an equivalent {@link MatchListener} for every enqueue it observes (NATS fans
 * {@code matchmaking.requests} out to every subscriber) - so those listeners are functionally
 * interchangeable across replicas, and {@link #addIfAbsent} being idempotent per userId is what keeps
 * the *shared* queue data itself correct despite each replica redundantly processing the same enqueue.
 * A replica that never observed a given userId's original enqueue (e.g. it joined after the fact, in
 * a future dynamic-scaling scenario) has no local listener for it and must skip that entry rather than
 * crash - see {@link MatchmakingServiceImpl}'s pairing loop.
 */
public interface WaitingPlayersQueue {
    /** No-op if {@code player.userId()} is already queued - see this interface's javadoc for why
     *  that idempotency matters. */
    void addIfAbsent(WaitingPlayer player);

    /** @return true if userId was actually removed - false if it wasn't queued (already matched,
     *  expired, or cancelled by a racing caller). */
    boolean remove(long userId);

    List<WaitingPlayer> snapshot();
}
