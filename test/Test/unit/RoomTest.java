package Test.unit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import server.auth.User;
import server.rooms.Room;

import static org.junit.jupiter.api.Assertions.*;

public class RoomTest {

    private Room room;
    private User alice;
    private User bob;
    private User carol;

    @BeforeEach
    public void setUp() {
        room = new Room("ROOM1");
        alice = new User(1, "alice", 1200);
        bob = new User(2, "bob", 1250);
        carol = new User(3, "carol", 1100);
    }

    @Test
    public void testFirstJoinerReservesWhite() {
        assertEquals(Room.Role.WHITE, room.reserveRole(alice));
    }

    @Test
    public void testSecondJoinerReservesBlack() {
        room.reserveRole(alice);
        assertEquals(Room.Role.BLACK, room.reserveRole(bob));
    }

    @Test
    public void testThirdJoinerBecomesSpectator() {
        room.reserveRole(alice);
        room.reserveRole(bob);
        assertEquals(Room.Role.SPECTATOR, room.reserveRole(carol));
    }

    @Test
    public void testReservingRoleIsIdempotentForSameUser() {
        room.reserveRole(alice);
        assertEquals(Room.Role.WHITE, room.reserveRole(alice));
    }

    @Test
    public void testIsFullOnlyAfterBothColorsTaken() {
        assertFalse(room.isFull());
        room.reserveRole(alice);
        assertFalse(room.isFull());
        room.reserveRole(bob);
        assertTrue(room.isFull());
    }

    @Test
    public void testGetUserByColor() {
        room.reserveRole(alice);
        room.reserveRole(bob);
        assertEquals(alice, room.getUser('w'));
        assertEquals(bob, room.getUser('b'));
    }

    @Test
    public void testGetUserById() {
        room.reserveRole(alice);
        assertEquals(alice, room.getUser(alice.getId()));
        assertNull(room.getUser(999L));
    }

    @Test
    public void testNotStartedUntilMarkStartedIsCalled() {
        assertFalse(room.isStarted());
        room.markStarted();
        assertTrue(room.isStarted());
    }
}
