package com.thorium.infrastructure.persistence;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.logging.Logger;

public class SQLiteConnectionProvider implements AutoCloseable {

    private static final Logger LOG = Logger.getLogger(SQLiteConnectionProvider.class.getName());

    private final String jdbcUrl;

    public SQLiteConnectionProvider(Path databasePath) {
        this.jdbcUrl = "jdbc:sqlite:" + databasePath.toAbsolutePath();
    }

    public Connection getConnection() throws SQLException {
        Connection connection = DriverManager.getConnection(jdbcUrl);
        connection.setAutoCommit(false);
        try (var stmt = connection.createStatement()) {
            stmt.execute("PRAGMA foreign_keys = ON");
        }
        return connection;
    }

    @Override
    public void close() {
        LOG.fine("SQLiteConnectionProvider closed (connections managed by callers)");
    }
}
