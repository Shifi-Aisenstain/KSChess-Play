package Test.unit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import server.auth.SqliteUserRepository;
import server.auth.UserRepository;
import server.persistence.SchemaInitializer;
import server.persistence.SqliteConnectionProvider;

import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/** Exercises the real SQLite-backed repository against a throwaway DB file per test (via @TempDir),
  * rather than mocking JDBC - this is the code SchemaInitializer/AuthServiceImpl actually run in
  * production, so it's worth verifying against a real driver, not just an in-memory fake. */
public class SqliteUserRepositoryTest {

    private SqliteUserRepository repository;

    @BeforeEach
    public void setUp(@TempDir Path tempDir) {
        String dbFile = tempDir.resolve("test.db").toString();
        SqliteConnectionProvider connectionProvider = new SqliteConnectionProvider(dbFile);
        new SchemaInitializer(connectionProvider).initialize();
        repository = new SqliteUserRepository(connectionProvider);
    }

    @Test
    public void testFindByUsernameReturnsEmptyWhenUnknown() {
        assertTrue(repository.findByUsername("nobody").isEmpty());
    }

    @Test
    public void testCreateThenFindReturnsTheSameUser() {
        UserRepository.StoredUser created = repository.create("alice", "hash", "salt", 1200);

        Optional<UserRepository.StoredUser> found = repository.findByUsername("alice");

        assertTrue(found.isPresent());
        assertEquals(created.id, found.get().id);
        assertEquals("alice", found.get().username);
        assertEquals("hash", found.get().passwordHash);
        assertEquals("salt", found.get().salt);
        assertEquals(1200, found.get().elo);
    }

    @Test
    public void testCreateAssignsIncreasingIds() {
        UserRepository.StoredUser first = repository.create("alice", "h", "s", 1200);
        UserRepository.StoredUser second = repository.create("bob", "h", "s", 1200);

        assertTrue(second.id > first.id);
    }

    @Test
    public void testUpdateEloPersists() {
        UserRepository.StoredUser created = repository.create("alice", "h", "s", 1200);

        repository.updateElo(created.id, 1350);

        assertEquals(1350, repository.findByUsername("alice").orElseThrow().elo);
    }

    /** Server_Design.md step 5: SqliteUserRepository doesn't override {@code updateEloBatch}, so
     *  this exercises the interface's default implementation (loop {@code updateElo} per entry)
     *  against a real database - {@code PostgresUserRepository} overrides it for one real batched
     *  round trip instead (see pom.xml's jacoco excludes for why that one needs a live Postgres). */
    @Test
    public void testUpdateEloBatchDefaultAppliesEveryEntry() {
        UserRepository.StoredUser alice = repository.create("alice", "h", "s", 1200);
        UserRepository.StoredUser bob = repository.create("bob", "h", "s", 1200);

        repository.updateEloBatch(Map.of(alice.id, 1350, bob.id, 1050));

        assertEquals(1350, repository.findByUsername("alice").orElseThrow().elo);
        assertEquals(1050, repository.findByUsername("bob").orElseThrow().elo);
    }

    @Test
    public void testSchemaInitializerIsIdempotent(@TempDir Path tempDir) {
        SqliteConnectionProvider connectionProvider =
                new SqliteConnectionProvider(tempDir.resolve("idempotent.db").toString());
        SchemaInitializer initializer = new SchemaInitializer(connectionProvider);

        initializer.initialize();
        assertDoesNotThrow(initializer::initialize);
    }
}
