package Test.unit;

import org.junit.jupiter.api.Test;
import server.persistence.BusEloWriter;
import server.rating.EloWriter;
import shared.bus.InMemoryMessageBus;
import shared.bus.MessageBus;
import shared.bus.RoomBusSubjects;
import shared.protocol.MessageCodec;
import shared.protocol.payload.EloUpdateRequestedPayload;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

public class BusEloWriterTest {

    @Test
    public void testUpdateEloPublishesExpectedPayload() {
        MessageBus bus = new InMemoryMessageBus();
        AtomicReference<EloUpdateRequestedPayload> captured = new AtomicReference<>();
        bus.subscribe(RoomBusSubjects.ELO_UPDATES, (message, replyTo) ->
                captured.set(MessageCodec.decodePayload(message, EloUpdateRequestedPayload.class)));

        EloWriter writer = new BusEloWriter(bus);
        writer.updateElo(42, 1350);

        assertNotNull(captured.get());
        assertEquals(42, captured.get().userId);
        assertEquals(1350, captured.get().newElo);
    }
}
