package server.auth;

import server.persistence.PostgresConnectionProvider;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.Optional;

/**
 * {@link UserRepository} backed by Postgres instead of SQLite (Server_Design.md step 5) - same
 * interface as {@link SqliteUserRepository}, so nothing else in the app (AuthService, GameSession's
 * {@code EloWriter} chain, etc.) needs to know or care which one is actually wired up; that choice
 * lives entirely in {@code server.config.PersistenceConfig}.
 */
public final class PostgresUserRepository implements UserRepository {
    private final PostgresConnectionProvider connectionProvider;

    public PostgresUserRepository(PostgresConnectionProvider connectionProvider) {
        this.connectionProvider = connectionProvider;
    }

    @Override
    public Optional<StoredUser> findByUsername(String username) {
        String sql = "SELECT id, username, password_hash, salt, elo FROM users WHERE username = ?";
        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, username);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                return Optional.of(new StoredUser(
                        rs.getLong("id"), rs.getString("username"),
                        rs.getString("password_hash"), rs.getString("salt"), rs.getInt("elo")));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("findByUsername failed", e);
        }
    }

    @Override
    public StoredUser create(String username, String passwordHash, String salt, int startingElo) {
        // Unlike sqlite-jdbc (see SqliteUserRepository's comment), the postgresql driver properly
        // implements PreparedStatement.getGeneratedKeys() for Statement.RETURN_GENERATED_KEYS - no
        // last_insert_rowid()-style workaround needed here.
        String sql = "INSERT INTO users (username, password_hash, salt, elo, created_at) VALUES (?, ?, ?, ?, ?)";
        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, username);
            statement.setString(2, passwordHash);
            statement.setString(3, salt);
            statement.setInt(4, startingElo);
            statement.setLong(5, System.currentTimeMillis());
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                long id = keys.next() ? keys.getLong(1) : -1;
                return new StoredUser(id, username, passwordHash, salt, startingElo);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("create user failed", e);
        }
    }

    @Override
    public void updateElo(long userId, int newElo) {
        String sql = "UPDATE users SET elo = ? WHERE id = ?";
        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, newElo);
            statement.setLong(2, userId);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("updateElo failed", e);
        }
    }

    /**
     * Overrides the interface's default (which just loops {@link #updateElo}) with one actual
     * round trip: every row goes into the same JDBC batch, committed as a single transaction, on
     * one connection - this is the real "drains into Postgres in batches" Server_Design.md step 5
     * asks for, exercised by {@code server.persistence.EloWriterMain}.
     */
    @Override
    public void updateEloBatch(Map<Long, Integer> newEloByUserId) {
        if (newEloByUserId.isEmpty()) return;
        String sql = "UPDATE users SET elo = ? WHERE id = ?";
        try (Connection connection = connectionProvider.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                for (Map.Entry<Long, Integer> entry : newEloByUserId.entrySet()) {
                    statement.setInt(1, entry.getValue());
                    statement.setLong(2, entry.getKey());
                    statement.addBatch();
                }
                statement.executeBatch();
                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("updateEloBatch failed", e);
        }
    }
}
