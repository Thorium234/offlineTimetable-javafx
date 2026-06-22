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
    private static final int SCHEMA_VERSION = 9;

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
            if (currentVersion < 4) {
                runMigrationV4(statement);
                setVersion(statement, 4);
            }
            if (currentVersion < 5) {
                runMigrationV5(statement);
                setVersion(statement, 5);
            }
            if (currentVersion < 6) {
                runMigrationV6(statement);
                setVersion(statement, 6);
            }
            if (currentVersion < 7) {
                runMigrationV7(statement);
                setVersion(statement, 7);
            }
            if (currentVersion < 8) {
                runMigrationV8(statement);
                setVersion(statement, 8);
            }
            if (currentVersion < 9) {
                runMigrationV9(statement);
                setVersion(statement, 9);
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

    private void runMigrationV4(Statement statement) throws SQLException {
        try {
            statement.execute("ALTER TABLE subjects ADD COLUMN color TEXT");
        } catch (SQLException e) {
            if (!e.getMessage().contains("duplicate column")) {
                throw e;
            }
        }
    }

    private void runMigrationV5(Statement statement) throws SQLException {
        statement.execute("""
                CREATE TABLE IF NOT EXISTS school_settings (
                    id                  INTEGER PRIMARY KEY CHECK (id = 1),
                    total_periods       INTEGER NOT NULL DEFAULT 8,
                    school_start_time   TEXT    NOT NULL DEFAULT '08:00',
                    school_end_time     TEXT    NOT NULL DEFAULT '16:00',
                    period_duration_min INTEGER NOT NULL DEFAULT 40
                )
                """);
    }

    private void runMigrationV6(Statement statement) throws SQLException {
        try {
            statement.execute("ALTER TABLE school_settings ADD COLUMN school_end_time TEXT NOT NULL DEFAULT '16:00'");
        } catch (SQLException e) {
            if (!e.getMessage().contains("duplicate column")) throw e;
        }
    }

    private void runMigrationV7(Statement statement) throws SQLException {
        try {
            statement.execute("ALTER TABLE breaks ADD COLUMN start_time TEXT");
        } catch (SQLException e) {
            if (!e.getMessage().contains("duplicate column")) throw e;
        }
        try {
            statement.execute("ALTER TABLE breaks ADD COLUMN end_time TEXT");
        } catch (SQLException e) {
            if (!e.getMessage().contains("duplicate column")) throw e;
        }
    }

    private void runMigrationV8(Statement statement) throws SQLException {
        try {
            statement.execute("ALTER TABLE breaks ADD COLUMN is_before_period_one INTEGER NOT NULL DEFAULT 0");
        } catch (SQLException e) {
            if (!e.getMessage().contains("duplicate column")) throw e;
        }
    }

    private void runMigrationV9(Statement statement) throws SQLException {
        try {
            statement.execute("ALTER TABLE breaks ADD COLUMN slotable INTEGER NOT NULL DEFAULT 0");
        } catch (SQLException e) {
            if (!e.getMessage().contains("duplicate column")) throw e;
        }
        try {
            statement.execute("ALTER TABLE timetable_entries ADD COLUMN slot_type TEXT NOT NULL DEFAULT 'PERIOD'");
        } catch (SQLException e) {
            if (!e.getMessage().contains("duplicate column")) throw e;
        }
        try {
            statement.execute("ALTER TABLE timetable_entries ADD COLUMN break_id INTEGER REFERENCES breaks(id) ON DELETE SET NULL");
        } catch (SQLException e) {
            if (!e.getMessage().contains("duplicate column")) throw e;
        }
        statement.execute("UPDATE breaks SET slotable = 1 WHERE name = 'Assembly'");
        statement.execute("UPDATE timetable_entries SET period_number = period_number + 1");
        statement.execute("UPDATE teacher_availability SET period_number = period_number + 1");
    }

    private void seedDefaults() {
        try (Connection connection = connectionProvider.getConnection();
             Statement statement = connection.createStatement()) {

            var settingsCount = connection.createStatement()
                    .executeQuery("SELECT COUNT(*) FROM school_settings");
            if (settingsCount.next() && settingsCount.getInt(1) == 0) {
                statement.execute("INSERT INTO school_settings (id, total_periods, school_start_time, school_end_time, period_duration_min) VALUES (1, 10, '07:10', '18:45', 40)");
            }

            var breakCount = connection.createStatement()
                    .executeQuery("SELECT COUNT(*) FROM breaks");
            if (breakCount.next() && breakCount.getInt(1) == 0) {
                statement.execute("INSERT INTO breaks (name, after_period, duration_minutes, sort_order, is_before_period_one, slotable, start_time, end_time) VALUES ('Assembly', 0, 50, 1, 1, 1, '07:10', '08:00')");
                statement.execute("INSERT INTO breaks (name, after_period, duration_minutes, sort_order, is_before_period_one, slotable, start_time, end_time) VALUES ('Tea Break', 3, 20, 2, 0, 0, '10:00', '10:20')");
                statement.execute("INSERT INTO breaks (name, after_period, duration_minutes, sort_order, is_before_period_one, slotable, start_time, end_time) VALUES ('Short Break', 5, 10, 3, 0, 0, '11:40', '11:50')");
                statement.execute("INSERT INTO breaks (name, after_period, duration_minutes, sort_order, is_before_period_one, slotable, start_time, end_time) VALUES ('Lunch Break', 7, 50, 4, 0, 0, '13:10', '14:00')");
                statement.execute("INSERT INTO breaks (name, after_period, duration_minutes, sort_order, is_before_period_one, slotable, start_time, end_time) VALUES ('Games Time', 10, 165, 5, 0, 0, '16:00', '18:45')");
            }

            var periodCount = connection.createStatement()
                    .executeQuery("SELECT COUNT(*) FROM periods");
            if (periodCount.next() && periodCount.getInt(1) == 0) {
                statement.execute("INSERT INTO periods (period_number, start_time, end_time, label) VALUES (1, '07:10', '08:00', 'Assembly')");
                LocalTime clock = LocalTime.of(8, 0);
                for (int i = 2; i <= 11; i++) {
                    LocalTime end = clock.plusMinutes(40);
                    statement.execute(String.format(
                            "INSERT INTO periods (period_number, start_time, end_time, label) VALUES (%d, '%s', '%s', 'P%d')",
                            i, clock.format(TIME_FORMAT), end.format(TIME_FORMAT), i - 1));
                    clock = end;
                    if (i == 4) clock = clock.plusMinutes(20);       // Tea after P3
                    else if (i == 6) clock = clock.plusMinutes(10);  // Short after P5
                    else if (i == 8) clock = clock.plusMinutes(50);  // Lunch after P7
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
