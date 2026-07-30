package server.config;

import server.auth.PostgresUserRepository;
import server.auth.SqliteUserRepository;
import server.auth.UserRepository;
import server.history.GameHistoryRepository;
import server.logging.ServerActivityLogger;
import server.persistence.PostgresConnectionProvider;
import server.persistence.PostgresGameHistoryRepository;
import server.persistence.PostgresSchemaInitializer;
import server.persistence.SchemaInitializer;
import server.persistence.SqliteConnectionProvider;
import server.persistence.SqliteGameHistoryRepository;

public final class PersistenceConfig {
    private static final String DEFAULT_SQLITE_FILE = "kungfu_chess.db";

    private PersistenceConfig() { }

    public static final class PersistenceBundle {
        public final UserRepository users;
        public final GameHistoryRepository history;

        public PersistenceBundle(UserRepository users, GameHistoryRepository history) {
            this.users = users;
            this.history = history;
        }
    }

    public static PersistenceBundle initialize(ServerActivityLogger activityLogger) {
        String postgresUrl = System.getenv("POSTGRES_URL");
        return (postgresUrl != null && !postgresUrl.isBlank())
                ? initializePostgres(postgresUrl, activityLogger)
                : initializeSqlite(System.getenv().getOrDefault("DB_FILE", DEFAULT_SQLITE_FILE), activityLogger);
    }

    private static PersistenceBundle initializeSqlite(String dbFilePath, ServerActivityLogger activityLogger) {
        try {
            SqliteConnectionProvider connectionProvider = new SqliteConnectionProvider(dbFilePath);
            new SchemaInitializer(connectionProvider).initialize();
            return new PersistenceBundle(new SqliteUserRepository(connectionProvider),
                    new SqliteGameHistoryRepository(connectionProvider));
        } catch (RuntimeException e) {

            fail("Could not initialize SQLite storage at '" + dbFilePath + "': " + e.getMessage(), e, activityLogger);
            throw new AssertionError("unreachable");
        }
    }

    private static PersistenceBundle initializePostgres(String jdbcUrl, ServerActivityLogger activityLogger) {
        try {
            PostgresConnectionProvider connectionProvider = new PostgresConnectionProvider(jdbcUrl);
            new PostgresSchemaInitializer(connectionProvider).initialize();
            return new PersistenceBundle(new PostgresUserRepository(connectionProvider),
                    new PostgresGameHistoryRepository(connectionProvider));
        } catch (RuntimeException e) {
            fail("Could not initialize Postgres storage at '" + jdbcUrl + "': " + e.getMessage(), e, activityLogger);
            throw new AssertionError("unreachable");
        }
    }

    private static void fail(String message, RuntimeException cause, ServerActivityLogger activityLogger) {
        System.err.println("[server] FATAL: " + message);
        activityLogger.logFatal("startup", message);
        throw new IllegalStateException("Server cannot start without a working database", cause);
    }
}
