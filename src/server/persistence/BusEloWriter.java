package server.persistence;

import server.rating.EloWriter;
import shared.bus.MessageBus;
import shared.bus.RoomBusSubjects;
import shared.protocol.MessageType;
import shared.protocol.payload.EloUpdateRequestedPayload;

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
