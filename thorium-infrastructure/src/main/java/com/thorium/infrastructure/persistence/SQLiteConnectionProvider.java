package com.thorium.infrastructure.persistence;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public class SQLiteConnectionProvider implements AutoCloseable {

    private static final Logger LOG = Logger.getLogger(SQLiteConnectionProvider.class.getName());

    private final String jdbcUrl;
    private final List<Connection> trackedConnections;

    public SQLiteConnectionProvider(Path databasePath) {
        this.jdbcUrl = "jdbc:sqlite:" + databasePath.toAbsolutePath();
        this.trackedConnections = new ArrayList<>();
    }

    public Connection getConnection() throws SQLException {
        Connection connection = DriverManager.getConnection(jdbcUrl);
        connection.setAutoCommit(false);
        try (var stmt = connection.createStatement()) {
            stmt.execute("PRAGMA foreign_keys = ON");
        }
        synchronized (trackedConnections) {
            trackedConnections.add(connection);
        }
        return connection;
    }

    @Override
    public void close() {
        synchronized (trackedConnections) {
            for (Connection conn : trackedConnections) {
                try {
                    if (conn != null && !conn.isClosed()) {
                        conn.close();
                    }
                } catch (SQLException e) {
                    LOG.warning("Failed to close connection: " + e.getMessage());
                }
            }
            trackedConnections.clear();
        }
    }
}
