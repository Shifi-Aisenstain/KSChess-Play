package server.persistence;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/** Creates the `users` table on first run against Postgres (Server_Design.md step 5) - mirrors
 *  {@link SchemaInitializer}'s role for SQLite. Idempotent (IF NOT EXISTS). Postgres-flavored DDL:
 *  {@code BIGSERIAL} instead of SQLite's {@code INTEGER PRIMARY KEY AUTOINCREMENT}, {@code BIGINT}
 *  for the millisecond timestamp (SQLite's dynamic typing let a plain {@code INTEGER} column hold
 *  that in {@link SchemaInitializer}, but Postgres is strictly typed).
 *
 * <p><b>Concurrent-startup race, and why this retries instead of just using IF NOT EXISTS:</b> more
 * than one process calls this against the same database on cold start (at least {@code apigateway}
 * and {@code elowriter} - see docker-compose.yml). {@code CREATE TABLE IF NOT EXISTS} on its own is
 * <i>not</i> safe against two sessions racing when the table has a {@code SERIAL}/{@code BIGSERIAL}
 * column: both can pass the "table doesn't exist yet" check before either commits, and the implicit
 * backing sequence ({@code users_id_seq}) each tries to create collides in Postgres's system catalog
 * (a duplicate-key violation on {@code pg_class_relname_nsp_index}, not on anything this class wrote
 * itself). The loser isn't actually wrong - by the time it fails, the winner's table already exists
 * and works fine - so this retries once after a short pause instead of treating that specific failure
 * as fatal; a second attempt's {@code IF NOT EXISTS} check now correctly sees the winner's
 * already-committed table and no-ops. */
public final class PostgresSchemaInitializer {
    private static final int MAX_ATTEMPTS = 3;
    private static final long RETRY_DELAY_MS = 250;

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

        SQLException lastFailure = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try (Connection connection = connectionProvider.getConnection();
                 Statement statement = connection.createStatement()) {
                statement.execute(ddl);
                return; // success (either created it, or IF NOT EXISTS correctly no-opped)
            } catch (SQLException e) {
                lastFailure = e;
                if (!looksLikeConcurrentCreateRace(e) || attempt == MAX_ATTEMPTS) break;
                sleepBeforeRetry();
            }
        }
        throw new IllegalStateException("Failed to initialize Postgres schema", lastFailure);
    }

    /** Postgres reports the concurrent-CREATE-TABLE race described in the class javadoc as a plain
     *  unique-violation (SQLSTATE 23505) against a system catalog index, or occasionally as
     *  duplicate_table (42P07) if the race lands slightly differently - both mean "someone else just
     *  created this, not a real schema problem". */
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
