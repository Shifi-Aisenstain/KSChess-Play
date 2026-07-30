package server.rating;

/**
 * The one persistence operation {@code server.game.GameSession} actually needs when a game ends -
 * deliberately narrower than the full {@code server.auth.UserRepository} (which also handles login/
 * registration - irrelevant to the game engine, and exactly the kind of dependency the game engine
 * shouldn't need per the Interface Segregation Principle). Server_Design.md step 5: the production
 * implementation ({@code server.persistence.NatsEloWriter}) publishes to NATS instead of writing to a
 * database directly - the actual write happens later, batched, in a separate process
 * ({@code server.persistence.EloWriterMain}).
 */
public interface EloWriter {
    void updateElo(long userId, int newElo);
}
