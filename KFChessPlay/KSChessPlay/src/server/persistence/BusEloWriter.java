package server.persistence;

import server.rating.EloWriter;
import shared.bus.MessageBus;
import shared.bus.RoomBusSubjects;
import shared.protocol.MessageType;
import shared.protocol.payload.EloUpdateRequestedPayload;

/**
 * Production {@link EloWriter} (Server_Design.md step 5): instead of writing to a database directly,
 * publishes a fire-and-forget {@code persistence.elo-updates} message and returns immediately - the
 * Game Server Shard that calls this never blocks on (or even knows about) how/when the write
 * actually lands in storage. {@link EloWriterMain} is the only thing that consumes these, batched.
 */
public final class BusEloWriter implements EloWriter {
    private final MessageBus bus;

    public BusEloWriter(MessageBus bus) {
        this.bus = bus;
    }

    @Override
    public void updateElo(long userId, int newElo) {
        bus.publish(RoomBusSubjects.ELO_UPDATES, MessageType.ELO_UPDATE_REQUESTED,
                new EloUpdateRequestedPayload(userId, newElo));
    }
}
