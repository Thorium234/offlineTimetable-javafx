package com.thorium.infrastructure.persistence;

import com.thorium.domain.value.ConstraintType;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public class DatabaseInitializer {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
    private static final int SCHEMA_VERSION = 3;

    private final SQLiteConnectionProvider connectionProvider;

    public DatabaseInitializer(SQLiteConnectionProvider connectionProvider) {
        this.connectionProvider = connectionProvider;
    }

    public void initialize() {
        try (Connection connection = connectionProvider.getConnection();
             Statement statement = connection.createStatement()) {
            createSchemaVersionTable(statement);
            int currentVersion = getCurrentVersion(connection);

            if (currentVersion < 1) {
                for (String sql : loadSchema().split(";")) {
                    String trimmed = sql.trim();
                    if (!trimmed.isEmpty()) {
                        statement.execute(trimmed);
                    }
                }
                setVersion(statement, 1);
            }
            if (currentVersion < 2) {
                runMigrationV2(statement);
                setVersion(statement, 2);
            }
            if (currentVersion < 3) {
                runMigrationV3(statement);
                setVersion(statement, 3);
            }

            connection.commit();
            seedDefaults();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to initialize database", e);
        }
    }

    private void createSchemaVersionTable(Statement statement) throws SQLException {
        statement.execute("CREATE TABLE IF NOT EXISTS schema_version (version INTEGER NOT NULL)");
    }

    private int getCurrentVersion(Connection connection) throws SQLException {
        try (var rs = connection.createStatement().executeQuery("SELECT MAX(version) FROM schema_version")) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    private void setVersion(Statement statement, int version) throws SQLException {
        statement.execute("INSERT INTO schema_version (version) VALUES (" + version + ")");
    }

    private void runMigrationV2(Statement statement) throws SQLException {
        try {
            statement.execute("ALTER TABLE subjects ADD COLUMN requires_double_period INTEGER NOT NULL DEFAULT 0 CHECK (requires_double_period IN (0, 1))");
        } catch (SQLException e) {
            if (!e.getMessage().contains("duplicate column")) {
                throw e;
            }
        }
    }

    private void runMigrationV3(Statement statement) throws SQLException {
        statement.execute("""
                CREATE TABLE IF NOT EXISTS rooms (
                    id       INTEGER PRIMARY KEY AUTOINCREMENT,
                    code     TEXT    NOT NULL UNIQUE,
                    name     TEXT    NOT NULL,
                    type     TEXT    NOT NULL DEFAULT 'CLASSROOM' CHECK (type IN ('CLASSROOM', 'LAB')),
                    capacity INTEGER NOT NULL DEFAULT 30 CHECK (capacity > 0)
                )
                """);
        try {
            statement.execute("ALTER TABLE timetable_entries ADD COLUMN room_id INTEGER REFERENCES rooms(id) ON DELETE SET NULL");
        } catch (SQLException e) {
            if (!e.getMessage().contains("duplicate column")) {
                throw e;
            }
        }
    }

    private void seedDefaults() {
        try (Connection connection = connectionProvider.getConnection();
             Statement statement = connection.createStatement()) {

            var periodCount = connection.createStatement()
                    .executeQuery("SELECT COUNT(*) FROM periods");
            if (periodCount.next() && periodCount.getInt(1) == 0) {
                LocalTime start = LocalTime.of(8, 0);
                for (int i = 1; i <= 8; i++) {
                    LocalTime end = start.plusMinutes(40);
                    statement.execute(String.format(
                            "INSERT INTO periods (period_number, start_time, end_time, label) VALUES (%d, '%s', '%s', 'P%d')",
                            i, start.format(TIME_FORMAT), end.format(TIME_FORMAT), i));
                    start = end;
                }
            }

            var constraintCount = connection.createStatement()
                    .executeQuery("SELECT COUNT(*) FROM constraints");
            if (constraintCount.next() && constraintCount.getInt(1) == 0) {
                for (ConstraintType type : ConstraintType.values()) {
                    statement.execute(String.format(
                            "INSERT INTO constraints (constraint_type, enabled, parameters) VALUES ('%s', 1, NULL)",
                            type.name()));
                }
            }

            connection.commit();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to seed database defaults", e);
        }
    }

    private String loadSchema() {
        try (InputStream stream = getClass().getResourceAsStream("/schema.sql")) {
            if (stream == null) {
                throw new IllegalStateException("schema.sql not found on classpath");
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read schema.sql", e);
        }
    }
}
