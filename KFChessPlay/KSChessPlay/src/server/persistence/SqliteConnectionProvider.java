package server.persistence;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Single point that knows how to obtain a JDBC connection to the server's
 * SQLite file. Kept as its own class (rather than each repository opening
 * connections ad-hoc) so the DB path/driver setup is configured exactly once
 * - a small Singleton-flavoured factory, injected into repositories rather
 * than referenced statically, which keeps the repositories unit-testable.
 */
public final class SqliteConnectionProvider {
    private final String jdbcUrl;

    public SqliteConnectionProvider(String dbFilePath) {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("sqlite-jdbc driver not found on classpath", e);
        }
        this.jdbcUrl = "jdbc:sqlite:" + dbFilePath;
    }

    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection(jdbcUrl);
    }
}
