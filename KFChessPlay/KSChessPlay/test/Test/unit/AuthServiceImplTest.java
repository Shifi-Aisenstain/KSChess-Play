package Test.unit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import server.auth.AuthService;
import server.auth.AuthServiceImpl;
import server.auth.PasswordHasher;
import server.auth.UserRepository;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

public class AuthServiceImplTest {

    /** Plain in-memory stand-in for SqliteUserRepository - keeps this test independent of a DB file. */
    private static final class InMemoryUserRepository implements UserRepository {
        private final Map<String, StoredUser> byUsername = new HashMap<>();
        private final AtomicLong nextId = new AtomicLong(1);

        @Override
        public Optional<StoredUser> findByUsername(String username) {
            return Optional.ofNullable(byUsername.get(username));
        }

        @Override
        public StoredUser create(String username, String passwordHash, String salt, int startingElo) {
            StoredUser user = new StoredUser(nextId.getAndIncrement(), username, passwordHash, salt, startingElo);
            byUsername.put(username, user);
            return user;
        }

        @Override
        public void updateElo(long userId, int newElo) {
            for (var entry : byUsername.entrySet()) {
                StoredUser u = entry.getValue();
                if (u.id == userId) {
                    entry.setValue(new StoredUser(u.id, u.username, u.passwordHash, u.salt, newElo));
                    return;
                }
            }
        }
    }

    private InMemoryUserRepository repository;
    private AuthServiceImpl authService;

    @BeforeEach
    public void setUp() {
        repository = new InMemoryUserRepository();
        authService = new AuthServiceImpl(repository, new PasswordHasher());
    }

    @Test
    public void testUnknownUsernameAutoRegistersAtStartingElo() {
        AuthService.Result result = authService.login("newplayer", "hunter2");

        assertTrue(result.success);
        assertEquals("newplayer", result.user.getUsername());
        assertEquals(1200, result.user.getElo());
        assertTrue(repository.findByUsername("newplayer").isPresent());
    }

    @Test
    public void testKnownUsernameWithCorrectPasswordSucceeds() {
        authService.login("alice", "correct-password");

        AuthService.Result result = authService.login("alice", "correct-password");

        assertTrue(result.success);
        assertEquals("alice", result.user.getUsername());
    }

    @Test
    public void testKnownUsernameWithWrongPasswordFails() {
        authService.login("alice", "correct-password");

        AuthService.Result result = authService.login("alice", "wrong-password");

        assertFalse(result.success);
        assertNotNull(result.errorMessage);
        assertNull(result.user);
    }

    @Test
    public void testPasswordIsNeverStoredInPlaintext() {
        authService.login("alice", "correct-password");

        UserRepository.StoredUser stored = repository.findByUsername("alice").orElseThrow();
        assertNotEquals("correct-password", stored.passwordHash);
    }

    @Test
    public void testBlankUsernameIsRejected() {
        AuthService.Result result = authService.login("  ", "password");
        assertFalse(result.success);
    }

    @Test
    public void testEmptyPasswordIsRejected() {
        AuthService.Result result = authService.login("alice", "");
        assertFalse(result.success);
    }

    @Test
    public void testSecondLoginDoesNotCreateADuplicateAccount() {
        AuthService.Result first = authService.login("bob", "pw");
        AuthService.Result second = authService.login("bob", "pw");

        assertEquals(first.user.getId(), second.user.getId());
    }
}
