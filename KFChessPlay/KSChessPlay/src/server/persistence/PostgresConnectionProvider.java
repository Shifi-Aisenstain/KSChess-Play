package server.persistence;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Single point that knows how to obtain a JDBC connection to Postgres (Server_Design.md step 5) -
 * mirrors {@link SqliteConnectionProvider}'s role for SQLite. {@code jdbcUrl} is expected to already
 * carry credentials as query params (e.g. {@code jdbc:postgresql://host:5432/db?user=u&password=p}),
 * so callers don't need a separate username/password pair to plumb through.
 */
public final class PostgresConnectionProvider {
    private final String jdbcUrl;

    public PostgresConnectionProvider(String jdbcUrl) {
        try {
            Class.forName("org.postgresql.Driver");
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("postgresql JDBC driver not found on classpath", e);
        }
        this.jdbcUrl = jdbcUrl;
    }

    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection(jdbcUrl);
    }
}
