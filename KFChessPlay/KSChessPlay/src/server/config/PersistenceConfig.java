package server.config;

import server.auth.PostgresUserRepository;
import server.auth.SqliteUserRepository;
import server.auth.UserRepository;
import server.logging.ServerActivityLogger;
import server.persistence.PostgresConnectionProvider;
import server.persistence.PostgresSchemaInitializer;
import server.persistence.SchemaInitializer;
import server.persistence.SqliteConnectionProvider;

/**
 * Wires the persistence layer and hands back the {@link UserRepository} the rest of the app depends
 * on. Server_Design.md step 5: "storage is pluggable" - SQLite stays the default for local dev/demo
 * ({@code DB_FILE}, no new env var needed); setting {@code POSTGRES_URL} switches to Postgres instead
 * (a JDBC URL, credentials included as query params - see {@link PostgresConnectionProvider}) without
 * any other class (AuthService, {@code server.persistence.EloWriterMain}, etc.) needing to know or
 * care which one is actually active.
 *
 * <p>Kept out of the composition roots (`ApiGatewayMain`/`EloWriterMain`) on purpose - a composition
 * root should assemble pre-built parts, not contain the SQL/connection-string knowledge itself.
 */
public final class PersistenceConfig {
    private static final String DEFAULT_SQLITE_FILE = "kungfu_chess.db";

    private PersistenceConfig() { }

    /**
     * @param activityLogger used to record a clear failure reason in the process's own activity log
     *                        in addition to stderr, so a failed startup leaves more than a bare
     *                        stack trace to debug from
     * @throws IllegalStateException if the database can't be opened or the schema can't be created;
     *         the caller cannot run without it, so this is fatal and intentionally not swallowed
     */
    public static UserRepository initialize(ServerActivityLogger activityLogger) {
        String postgresUrl = System.getenv("POSTGRES_URL");
        return (postgresUrl != null && !postgresUrl.isBlank())
                ? initializePostgres(postgresUrl, activityLogger)
                : initializeSqlite(System.getenv().getOrDefault("DB_FILE", DEFAULT_SQLITE_FILE), activityLogger);
    }

    private static UserRepository initializeSqlite(String dbFilePath, ServerActivityLogger activityLogger) {
        try {
            SqliteConnectionProvider connectionProvider = new SqliteConnectionProvider(dbFilePath);
            new SchemaInitializer(connectionProvider).initialize();
            return new SqliteUserRepository(connectionProvider);
        } catch (RuntimeException e) {
            // SqliteConnectionProvider/SchemaInitializer already wrap the real underlying failure
            // (missing driver, locked/corrupt file, bad DDL, ...) in an unchecked exception; without
            // this catch that exception would still crash the process (uncaught, at main()), just
            // with no indication of *what* subsystem or file was responsible.
            fail("Could not initialize SQLite storage at '" + dbFilePath + "': " + e.getMessage(), e, activityLogger);
            throw new AssertionError("unreachable"); // fail() always throws; keeps the compiler happy
        }
    }

    private static UserRepository initializePostgres(String jdbcUrl, ServerActivityLogger activityLogger) {
        try {
            PostgresConnectionProvider connectionProvider = new PostgresConnectionProvider(jdbcUrl);
            new PostgresSchemaInitializer(connectionProvider).initialize();
            return new PostgresUserRepository(connectionProvider);
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
