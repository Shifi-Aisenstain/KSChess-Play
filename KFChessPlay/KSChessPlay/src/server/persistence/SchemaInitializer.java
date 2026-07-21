package server.persistence;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/** Creates the `users` table on first run. Idempotent (IF NOT EXISTS). */
public final class SchemaInitializer {
    private final SqliteConnectionProvider connectionProvider;

    public SchemaInitializer(SqliteConnectionProvider connectionProvider) {
        this.connectionProvider = connectionProvider;
    }

    public void initialize() {
        String ddl = "CREATE TABLE IF NOT EXISTS users (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "username TEXT UNIQUE NOT NULL," +
                "password_hash TEXT NOT NULL," +
                "salt TEXT NOT NULL," +
                "elo INTEGER NOT NULL DEFAULT 1200," +
                "created_at INTEGER NOT NULL" +
                ")";
        try (Connection connection = connectionProvider.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute(ddl);
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to initialize SQLite schema", e);
        }
    }
}
