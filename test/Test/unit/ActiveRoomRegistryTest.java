package Test.unit;

import org.junit.jupiter.api.Test;
import server.allocator.ActiveRoomRegistry;
import server.allocator.InMemoryActiveRoomRegistry;

import static org.junit.jupiter.api.Assertions.*;

public class ActiveRoomRegistryTest {

    @Test
    public void testGetIsEmptyForUnknownUser() {
        ActiveRoomRegistry registry = new InMemoryActiveRoomRegistry();

        assertTrue(registry.get(999).isEmpty());
    }

    @Test
    public void testPutThenGetReturnsTheRoom() {
        ActiveRoomRegistry registry = new InMemoryActiveRoomRegistry();

        registry.put(1, "ROOM1");

        assertEquals("ROOM1", registry.get(1).orElse(null));
    }

    @Test
    public void testPutOverwritesPreviousValue() {
        ActiveRoomRegistry registry = new InMemoryActiveRoomRegistry();
        registry.put(1, "ROOM1");

        registry.put(1, "ROOM2");

        assertEquals("ROOM2", registry.get(1).orElse(null));
    }

    @Test
    public void testRemoveClearsTheEntry() {
        ActiveRoomRegistry registry = new InMemoryActiveRoomRegistry();
        registry.put(1, "ROOM1");

        registry.remove(1);

        assertTrue(registry.get(1).isEmpty());
    }

    @Test
    public void testRemoveOfUnknownUserIsSafeNoOp() {
        ActiveRoomRegistry registry = new InMemoryActiveRoomRegistry();

        assertDoesNotThrow(() -> registry.remove(999));
    }
}
