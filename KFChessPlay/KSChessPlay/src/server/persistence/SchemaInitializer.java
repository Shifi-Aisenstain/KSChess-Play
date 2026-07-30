package server.persistence;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public final class SchemaInitializer {
    private final SqliteConnectionProvider connectionProvider;

    public SchemaInitializer(SqliteConnectionProvider connectionProvider) {
        this.connectionProvider = connectionProvider;
    }

    private static final String USERS_DDL = "CREATE TABLE IF NOT EXISTS users (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT," +
            "username TEXT UNIQUE NOT NULL," +
            "password_hash TEXT NOT NULL," +
            "salt TEXT NOT NULL," +
            "elo INTEGER NOT NULL DEFAULT 1200," +
            "created_at INTEGER NOT NULL" +
            ")";

    private static final String GAMES_DDL = "CREATE TABLE IF NOT EXISTS games (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT," +
            "room_id TEXT NOT NULL," +
            "white_user_id INTEGER NOT NULL," +
            "black_user_id INTEGER NOT NULL," +
            "white_username TEXT NOT NULL," +
            "black_username TEXT NOT NULL," +
            "winner_color TEXT," +
            "reason TEXT NOT NULL," +
            "white_elo_delta INTEGER NOT NULL," +
            "black_elo_delta INTEGER NOT NULL," +
            "ended_at INTEGER NOT NULL" +
            ")";

    private static final String MOVES_DDL = "CREATE TABLE IF NOT EXISTS moves (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT," +
            "room_id TEXT NOT NULL," +
            "notation TEXT NOT NULL," +
            "played_at INTEGER NOT NULL" +
            ")";

    public void initialize() {
        try (Connection connection = connectionProvider.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute(USERS_DDL);
            statement.execute(GAMES_DDL);
            statement.execute(MOVES_DDL);
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to initialize SQLite schema", e);
        }
    }
}
