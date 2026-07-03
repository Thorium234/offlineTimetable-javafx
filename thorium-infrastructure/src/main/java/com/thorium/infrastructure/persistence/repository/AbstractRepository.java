package com.thorium.infrastructure.persistence.repository;

import com.thorium.infrastructure.persistence.SQLiteConnectionProvider;

import java.sql.Connection;
import java.sql.SQLException;

abstract class AbstractRepository {

    protected final SQLiteConnectionProvider connectionProvider;

    protected AbstractRepository(SQLiteConnectionProvider connectionProvider) {
        this.connectionProvider = connectionProvider;
    }

    protected Connection connection() throws SQLException {
        return connectionProvider.getConnection();
    }

    protected void commit(Connection connection) throws SQLException {
        connection.commit();
    }

    protected void rollback(Connection connection) {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.rollback();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to rollback transaction", e);
        }
    }

    protected <T> T executeWithRollback(SqlOperation<T> operation) {
        Connection conn = null;
        try {
            conn = connection();
            T result = operation.execute(conn);
            commit(conn);
            return result;
        } catch (SQLException e) {
            rollback(conn);
            throw new IllegalStateException("Database operation failed", e);
        } finally {
            if (conn != null) {
                try { conn.close(); } catch (SQLException ignored) {}
            }
        }
    }

    protected void executeWithRollbackVoid(SqlVoidOperation operation) {
        Connection conn = null;
        try {
            conn = connection();
            operation.execute(conn);
            commit(conn);
        } catch (SQLException e) {
            rollback(conn);
            throw new IllegalStateException("Database operation failed", e);
        } finally {
            if (conn != null) {
                try { conn.close(); } catch (SQLException ignored) {}
            }
        }
    }

    @FunctionalInterface
    protected interface SqlOperation<T> {
        T execute(Connection conn) throws SQLException;
    }

    @FunctionalInterface
    protected interface SqlVoidOperation {
        void execute(Connection conn) throws SQLException;
    }
}
