package Test.unit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import server.history.GameSummary;
import server.history.MoveEntry;
import server.persistence.SchemaInitializer;
import server.persistence.SqliteConnectionProvider;
import server.persistence.SqliteGameHistoryRepository;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class SqliteGameHistoryRepositoryTest {

    private SqliteConnectionProvider connectionProvider;
    private SqliteGameHistoryRepository repository;

    @BeforeEach
    public void setUp(@TempDir Path tempDir) {
        String dbFile = tempDir.resolve("history-test.db").toString();
        connectionProvider = new SqliteConnectionProvider(dbFile);
        new SchemaInitializer(connectionProvider).initialize();
        repository = new SqliteGameHistoryRepository(connectionProvider);
    }

    @Test
    public void testSaveGamePersistsAllFields() throws Exception {
        repository.saveGame("ROOM1", 1L, 2L, "alice", "bob", "WHITE", "checkmate", 16, -16, 12345L);

        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT room_id, white_user_id, black_user_id, white_username, black_username, "
                             + "winner_color, reason, white_elo_delta, black_elo_delta, ended_at FROM games");
             ResultSet rs = statement.executeQuery()) {
            assertTrue(rs.next());
            assertEquals("ROOM1", rs.getString("room_id"));
            assertEquals(1L, rs.getLong("white_user_id"));
            assertEquals(2L, rs.getLong("black_user_id"));
            assertEquals("alice", rs.getString("white_username"));
            assertEquals("bob", rs.getString("black_username"));
            assertEquals("WHITE", rs.getString("winner_color"));
            assertEquals("checkmate", rs.getString("reason"));
            assertEquals(16, rs.getInt("white_elo_delta"));
            assertEquals(-16, rs.getInt("black_elo_delta"));
            assertEquals(12345L, rs.getLong("ended_at"));
        }
    }

    @Test
    public void testSaveGameWithNullWinnerColorPersistsAsDraw() throws Exception {
        repository.saveGame("ROOM2", 3L, 4L, "carol", "dave", null, "draw", 0, 0, 99999L);

        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT winner_color, reason FROM games WHERE room_id = 'ROOM2'");
             ResultSet rs = statement.executeQuery()) {
            assertTrue(rs.next());
            assertNull(rs.getString("winner_color"), "a draw has no winner - the column should be NULL, not the literal string \"null\"");
            assertEquals("draw", rs.getString("reason"));
        }
    }

    @Test
    public void testSaveMovePersistsAllFields() throws Exception {
        repository.saveMove("ROOM1", "Qe2xe5", 12345L);

        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT room_id, notation, played_at FROM moves");
             ResultSet rs = statement.executeQuery()) {
            assertTrue(rs.next());
            assertEquals("ROOM1", rs.getString("room_id"));
            assertEquals("Qe2xe5", rs.getString("notation"));
            assertEquals(12345L, rs.getLong("played_at"));
        }
    }

    @Test
    public void testMovesArePersistedInInsertionOrder() throws Exception {
        repository.saveMove("ROOM1", "e4", 1L);
        repository.saveMove("ROOM1", "e5", 2L);
        repository.saveMove("ROOM1", "Nf3", 3L);

        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT notation FROM moves ORDER BY id");
             ResultSet rs = statement.executeQuery()) {
            assertTrue(rs.next());
            assertEquals("e4", rs.getString("notation"));
            assertTrue(rs.next());
            assertEquals("e5", rs.getString("notation"));
            assertTrue(rs.next());
            assertEquals("Nf3", rs.getString("notation"));
            assertFalse(rs.next());
        }
    }

    @Test
    public void testFindGamesByUserIdReturnsGamesForBothColorsMostRecentFirst() {
        repository.saveGame("ROOM1", 1L, 2L, "alice", "bob", "WHITE", "checkmate", 16, -16, 1000L);
        repository.saveGame("ROOM2", 3L, 1L, "carol", "alice", "BLACK", "resignation", -10, 10, 2000L);
        repository.saveGame("ROOM3", 4L, 5L, "dave", "eve", "WHITE", "checkmate", 16, -16, 3000L);

        List<GameSummary> games = repository.findGamesByUserId(1L);

        assertEquals(2, games.size());
        assertEquals("ROOM2", games.get(0).roomId);
        assertEquals("ROOM1", games.get(1).roomId);
    }

    @Test
    public void testFindGamesByUserIdReturnsEmptyListForUnknownUser() {
        repository.saveGame("ROOM1", 1L, 2L, "alice", "bob", "WHITE", "checkmate", 16, -16, 1000L);

        List<GameSummary> games = repository.findGamesByUserId(999L);

        assertTrue(games.isEmpty());
    }

    @Test
    public void testFindMovesByRoomIdReturnsOnlyMovesForThatRoomInOrder() {
        repository.saveMove("ROOM1", "e4", 1L);
        repository.saveMove("ROOM2", "d4", 2L);
        repository.saveMove("ROOM1", "e5", 3L);

        List<MoveEntry> moves = repository.findMovesByRoomId("ROOM1");

        assertEquals(2, moves.size());
        assertEquals("e4", moves.get(0).notation);
        assertEquals("e5", moves.get(1).notation);
    }

    @Test
    public void testFindMovesByRoomIdReturnsEmptyListForUnknownRoom() {
        List<MoveEntry> moves = repository.findMovesByRoomId("NOPE");

        assertTrue(moves.isEmpty());
    }
}
