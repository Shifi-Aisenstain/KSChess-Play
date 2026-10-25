package Test.unit;

import org.junit.jupiter.api.Test;
import server.rooms.RoomIdGenerator;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class RoomIdGeneratorTest {

    private final RoomIdGenerator generator = new RoomIdGenerator();

    @Test
    public void testGeneratesSixCharacterCode() {
        assertEquals(6, generator.generate().length());
    }

    @Test
    public void testExcludesAmbiguousCharacters() {
        String code = generator.generate();
        for (char c : code.toCharArray()) {
            assertFalse("0O1I".indexOf(c) >= 0, "code contained ambiguous character: " + c);
        }
    }

    @Test
    public void testConsecutiveCallsAreVeryUnlikelyToCollide() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            seen.add(generator.generate());
        }
        assertTrue(seen.size() > 990, "expected near-unique codes, got " + seen.size() + "/1000 unique");
    }

    @Test
    public void testCodeIsUppercaseAlphanumeric() {
        String code = generator.generate();
        assertTrue(code.matches("[A-Z2-9]+"));
    }
}
