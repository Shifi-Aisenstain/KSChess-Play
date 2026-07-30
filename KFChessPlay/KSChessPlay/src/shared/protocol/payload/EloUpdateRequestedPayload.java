package shared.protocol.payload;

import lombok.AllArgsConstructor;

/**
 * Published by a Game Server Shard when a game ends instead of writing Elo synchronously through
 * {@code UserRepository} (Server_Design.md step 5) - {@code server.persistence.EloWriterMain}
 * consumes these, batched, and is the only thing that actually writes them to storage.
 */
@AllArgsConstructor
public final class EloUpdateRequestedPayload {
    public final long userId;
    public final int newElo;
}
