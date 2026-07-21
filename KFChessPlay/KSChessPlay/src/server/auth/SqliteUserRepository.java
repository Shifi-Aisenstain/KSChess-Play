package server.auth;

import server.persistence.SqliteConnectionProvider;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Optional;

public final class SqliteUserRepository implements UserRepository {
    private final SqliteConnectionProvider connectionProvider;

    public SqliteUserRepository(SqliteConnectionProvider connectionProvider) {
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
        // sqlite-jdbc doesn't implement PreparedStatement.getGeneratedKeys() for
        // Statement.RETURN_GENERATED_KEYS (throws SQLFeatureNotSupportedException) -
        // last_insert_rowid() is the driver's documented alternative.
        String sql = "INSERT INTO users (username, password_hash, salt, elo, created_at) VALUES (?, ?, ?, ?, ?)";
        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, username);
            statement.setString(2, passwordHash);
            statement.setString(3, salt);
            statement.setInt(4, startingElo);
            statement.setLong(5, System.currentTimeMillis());
            statement.executeUpdate();
            try (Statement idStatement = connection.createStatement();
                 ResultSet keys = idStatement.executeQuery("SELECT last_insert_rowid()")) {
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
}
