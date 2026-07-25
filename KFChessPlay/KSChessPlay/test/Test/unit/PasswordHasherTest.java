package Test.unit;

import org.junit.jupiter.api.Test;
import server.auth.PasswordHasher;

import static org.junit.jupiter.api.Assertions.*;

public class PasswordHasherTest {

    private final PasswordHasher hasher = new PasswordHasher();

    @Test
    public void testGenerateSaltProducesDifferentValuesEachTime() {
        String salt1 = hasher.generateSalt();
        String salt2 = hasher.generateSalt();
        assertNotEquals(salt1, salt2);
    }

    @Test
    public void testHashIsDeterministicForSamePasswordAndSalt() {
        String salt = hasher.generateSalt();
        assertEquals(hasher.hash("secret", salt), hasher.hash("secret", salt));
    }

    @Test
    public void testHashDiffersForDifferentPasswords() {
        String salt = hasher.generateSalt();
        assertNotEquals(hasher.hash("secret", salt), hasher.hash("different", salt));
    }

    @Test
    public void testHashDiffersForDifferentSalts() {
        String salt1 = hasher.generateSalt();
        String salt2 = hasher.generateSalt();
        assertNotEquals(hasher.hash("secret", salt1), hasher.hash("secret", salt2));
    }

    @Test
    public void testHashNeverEqualsPlaintextPassword() {
        String salt = hasher.generateSalt();
        assertNotEquals("secret", hasher.hash("secret", salt));
    }

    @Test
    public void testMatchesReturnsTrueForCorrectPassword() {
        String salt = hasher.generateSalt();
        String hash = hasher.hash("secret", salt);
        assertTrue(hasher.matches("secret", salt, hash));
    }

    @Test
    public void testMatchesReturnsFalseForWrongPassword() {
        String salt = hasher.generateSalt();
        String hash = hasher.hash("secret", salt);
        assertFalse(hasher.matches("wrong-password", salt, hash));
    }
}
