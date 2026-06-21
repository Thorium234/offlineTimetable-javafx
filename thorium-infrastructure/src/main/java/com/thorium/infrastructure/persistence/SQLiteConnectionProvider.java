package com.thorium.infrastructure.persistence;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class SQLiteConnectionProvider {

    private final String jdbcUrl;
    private Connection sharedConnection;

    public SQLiteConnectionProvider(Path databasePath) {
        this.jdbcUrl = "jdbc:sqlite:" + databasePath.toAbsolutePath();
    }

    public Connection getConnection() throws SQLException {
        if (sharedConnection == null || sharedConnection.isClosed()) {
            sharedConnection = DriverManager.getConnection(jdbcUrl);
            sharedConnection.setAutoCommit(false);
            try (var stmt = sharedConnection.createStatement()) {
                stmt.execute("PRAGMA foreign_keys = ON");
            }
        }
        return sharedConnection;
    }

    public void close() {
        if (sharedConnection != null) {
            try {
                sharedConnection.close();
            } catch (SQLException ignored) {
            }
        }
    }
}
