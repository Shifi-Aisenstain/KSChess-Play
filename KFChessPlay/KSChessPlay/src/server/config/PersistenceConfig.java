package server.config;

import server.auth.SqliteUserRepository;
import server.auth.UserRepository;
import server.logging.ServerActivityLogger;
import server.persistence.SchemaInitializer;
import server.persistence.SqliteConnectionProvider;

/**
 * Wires the SQLite-backed persistence layer: opens the JDBC connection, makes sure the schema
 * exists, and hands back the {@link UserRepository} the rest of the app depends on.
 *
 * <p>Kept out of {@code ServerController} on purpose - a composition root should assemble pre-built
 * parts, not contain the SQL/connection-string knowledge itself (that's what let this failure
 * mode go unguarded before: a raw {@code SqliteConnectionProvider}/{@code SchemaInitializer} call
 * inline in {@code main} with no {@code try-catch} around it).
 */
public final class PersistenceConfig {
    private PersistenceConfig() { }

    /**
     * @param dbFilePath    path to the SQLite database file (created on first run)
     * @param activityLogger used to record a clear failure reason in {@code server_activity.log}
     *                        in addition to stderr, so a failed startup leaves more than a bare
     *                        stack trace to debug from
     * @throws IllegalStateException if the database can't be opened or the schema can't be created;
     *         the server cannot run without it, so this is fatal and intentionally not swallowed
     */
    public static UserRepository initialize(String dbFilePath, ServerActivityLogger activityLogger) {
        try {
            SqliteConnectionProvider connectionProvider = new SqliteConnectionProvider(dbFilePath);
            new SchemaInitializer(connectionProvider).initialize();
            return new SqliteUserRepository(connectionProvider);
        } catch (RuntimeException e) {
            // SqliteConnectionProvider/SchemaInitializer already wrap the real underlying failure
            // (missing driver, locked/corrupt file, bad DDL, ...) in an unchecked exception; without
            // this catch that exception would still crash the process (uncaught, at main()), just
            // with no indication of *what* subsystem or file was responsible.
            String message = "Could not initialize SQLite storage at '" + dbFilePath + "': " + e.getMessage();
            System.err.println("[server] FATAL: " + message);
            activityLogger.logFatal("startup", message);
            throw new IllegalStateException("Server cannot start without a working database", e);
        }
    }
}
