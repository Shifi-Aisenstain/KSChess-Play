package server.persistence;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/** Creates the `users` table on first run against Postgres (Server_Design.md step 5) - mirrors
 *  {@link SchemaInitializer}'s role for SQLite. Idempotent (IF NOT EXISTS). Postgres-flavored DDL:
 *  {@code BIGSERIAL} instead of SQLite's {@code INTEGER PRIMARY KEY AUTOINCREMENT}, {@code BIGINT}
 *  for the millisecond timestamp (SQLite's dynamic typing let a plain {@code INTEGER} column hold
 *  that in {@link SchemaInitializer}, but Postgres is strictly typed). */
public final class PostgresSchemaInitializer {
    private final PostgresConnectionProvider connectionProvider;

    public PostgresSchemaInitializer(PostgresConnectionProvider connectionProvider) {
        this.connectionProvider = connectionProvider;
    }

    public void initialize() {
        String ddl = "CREATE TABLE IF NOT EXISTS users (" +
                "id BIGSERIAL PRIMARY KEY," +
                "username TEXT UNIQUE NOT NULL," +
                "password_hash TEXT NOT NULL," +
                "salt TEXT NOT NULL," +
                "elo INTEGER NOT NULL DEFAULT 1200," +
                "created_at BIGINT NOT NULL" +
                ")";
        try (Connection connection = connectionProvider.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute(ddl);
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to initialize Postgres schema", e);
        }
    }
}
