package server.persistence;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

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
