package server.persistence;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public final class PostgresSchemaInitializer {
    private static final int MAX_ATTEMPTS = 3;
    private static final long RETRY_DELAY_MS = 250;

    private final PostgresConnectionProvider connectionProvider;

    public PostgresSchemaInitializer(PostgresConnectionProvider connectionProvider) {
        this.connectionProvider = connectionProvider;
    }

    private static final String USERS_DDL = "CREATE TABLE IF NOT EXISTS users (" +
            "id BIGSERIAL PRIMARY KEY," +
            "username TEXT UNIQUE NOT NULL," +
            "password_hash TEXT NOT NULL," +
            "salt TEXT NOT NULL," +
            "elo INTEGER NOT NULL DEFAULT 1200," +
            "created_at BIGINT NOT NULL" +
            ")";

    private static final String GAMES_DDL = "CREATE TABLE IF NOT EXISTS games (" +
            "id BIGSERIAL PRIMARY KEY," +
            "room_id TEXT NOT NULL," +
            "white_user_id BIGINT NOT NULL," +
            "black_user_id BIGINT NOT NULL," +
            "white_username TEXT NOT NULL," +
            "black_username TEXT NOT NULL," +
            "winner_color TEXT," +
            "reason TEXT NOT NULL," +
            "white_elo_delta INTEGER NOT NULL," +
            "black_elo_delta INTEGER NOT NULL," +
            "ended_at BIGINT NOT NULL" +
            ")";

    private static final String MOVES_DDL = "CREATE TABLE IF NOT EXISTS moves (" +
            "id BIGSERIAL PRIMARY KEY," +
            "room_id TEXT NOT NULL," +
            "notation TEXT NOT NULL," +
            "played_at BIGINT NOT NULL" +
            ")";

    public void initialize() {
        for (String ddl : new String[] { USERS_DDL, GAMES_DDL, MOVES_DDL }) {
            runWithRetry(ddl);
        }
    }

    private void runWithRetry(String ddl) {
        SQLException lastFailure = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try (Connection connection = connectionProvider.getConnection();
                 Statement statement = connection.createStatement()) {
                statement.execute(ddl);
                return;
            } catch (SQLException e) {
                lastFailure = e;
                if (!looksLikeConcurrentCreateRace(e) || attempt == MAX_ATTEMPTS) break;
                sleepBeforeRetry();
            }
        }
        throw new IllegalStateException("Failed to initialize Postgres schema", lastFailure);
    }

    private static boolean looksLikeConcurrentCreateRace(SQLException e) {
        String sqlState = e.getSQLState();
        return "23505".equals(sqlState) || "42P07".equals(sqlState);
    }

    private static void sleepBeforeRetry() {
        try {
            Thread.sleep(RETRY_DELAY_MS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
