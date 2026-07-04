package com.thorium.infrastructure.persistence;

import com.thorium.domain.value.ConstraintType;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.logging.Logger;

public class DatabaseInitializer {

    private static final Logger LOG = Logger.getLogger(DatabaseInitializer.class.getName());

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
    private static final int SCHEMA_VERSION = 14;

    private final SQLiteConnectionProvider connectionProvider;

    public DatabaseInitializer(SQLiteConnectionProvider connectionProvider) {
        this.connectionProvider = connectionProvider;
    }

    public void initialize() {
        LOG.info("Initializing database schema");
        long start = System.currentTimeMillis();
        try (Connection connection = connectionProvider.getConnection()) {
            createSchemaVersionTable(connection);
            int currentVersion = getCurrentVersion(connection);
            LOG.fine("Current schema version: " + currentVersion);

            if (currentVersion < 1) {
                for (String sql : loadSchema().split(";")) {
                    String trimmed = sql.trim();
                    if (!trimmed.isEmpty()) {
                        try (Statement stmt = connection.createStatement()) {
                            stmt.execute(trimmed);
                        }
                    }
                }
                setVersion(connection, 1);
            }
            if (currentVersion < 2) {
                try (Statement stmt = connection.createStatement()) {
                    runMigrationV2(stmt);
                }
                setVersion(connection, 2);
            }
            if (currentVersion < 3) {
                try (Statement stmt = connection.createStatement()) {
                    runMigrationV3(stmt);
                }
                setVersion(connection, 3);
            }
            if (currentVersion < 4) {
                try (Statement stmt = connection.createStatement()) {
                    runMigrationV4(stmt);
                }
                setVersion(connection, 4);
            }
            if (currentVersion < 5) {
                try (Statement stmt = connection.createStatement()) {
                    runMigrationV5(stmt);
                }
                setVersion(connection, 5);
            }
            if (currentVersion < 6) {
                try (Statement stmt = connection.createStatement()) {
                    runMigrationV6(stmt);
                }
                setVersion(connection, 6);
            }
            if (currentVersion < 7) {
                try (Statement stmt = connection.createStatement()) {
                    runMigrationV7(stmt);
                }
                setVersion(connection, 7);
            }
            if (currentVersion < 8) {
                try (Statement stmt = connection.createStatement()) {
                    runMigrationV8(stmt);
                }
                setVersion(connection, 8);
            }
            if (currentVersion < 9) {
                try (Statement stmt = connection.createStatement()) {
                    runMigrationV9(stmt);
                }
                setVersion(connection, 9);
            }
            if (currentVersion < 10) {
                try (Statement stmt = connection.createStatement()) {
                    runMigrationV10(stmt);
                }
                setVersion(connection, 10);
            }
            if (currentVersion < 11) {
                try (Statement stmt = connection.createStatement()) {
                    runMigrationV11(stmt);
                }
                setVersion(connection, 11);
            }
            if (currentVersion < 12) {
                try (Statement stmt = connection.createStatement()) {
                    runMigrationV12(stmt);
                }
                setVersion(connection, 12);
            }
            if (currentVersion < 13) {
                try (Statement stmt = connection.createStatement()) {
                    runMigrationV13(stmt);
                }
                setVersion(connection, 13);
            }
            if (currentVersion < 14) {
                try (Statement stmt = connection.createStatement()) {
                    runMigrationV14(stmt);
                }
                setVersion(connection, 14);
            }

            connection.commit();
            seedDefaults();
            long elapsed = System.currentTimeMillis() - start;
            LOG.info("Database initialization complete (" + elapsed + "ms, version " + getCurrentVersion(connection) + ")");
        } catch (SQLException e) {
            LOG.severe("Database initialization failed: " + e.getMessage());
            throw new IllegalStateException("Failed to initialize database", e);
        }
    }

    private void createSchemaVersionTable(Connection connection) throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS schema_version (version INTEGER NOT NULL)");
        }
    }

    private int getCurrentVersion(Connection connection) throws SQLException {
        try (var rs = connection.createStatement().executeQuery("SELECT MAX(version) FROM schema_version")) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    private void setVersion(Connection connection, int version) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("INSERT INTO schema_version (version) VALUES (?)")) {
            ps.setInt(1, version);
            ps.executeUpdate();
        }
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
        statement.execute("CREATE TABLE IF NOT EXISTS timetable_entries_backup AS SELECT * FROM timetable_entries");
        statement.execute("""
                CREATE TABLE timetable_entries_v2 (
                    id                     INTEGER PRIMARY KEY AUTOINCREMENT,
                    timetable_id           INTEGER NOT NULL REFERENCES timetables(id) ON DELETE CASCADE,
                    teaching_assignment_id INTEGER NOT NULL REFERENCES teaching_assignments(id) ON DELETE CASCADE,
                    day_of_week            TEXT    NOT NULL,
                    period_number          INTEGER NOT NULL CHECK (period_number > 0),
                    room_id                INTEGER REFERENCES rooms(id) ON DELETE SET NULL,
                    slot_type              TEXT    NOT NULL DEFAULT 'PERIOD',
                    break_id               INTEGER REFERENCES breaks(id) ON DELETE SET NULL,
                    UNIQUE (timetable_id, teaching_assignment_id, day_of_week, period_number)
                )
                """);
        statement.execute("INSERT INTO timetable_entries_v2 (timetable_id, teaching_assignment_id, day_of_week, period_number, room_id, slot_type) SELECT timetable_id, teaching_assignment_id, day_of_week, period_number + 1, room_id, 'PERIOD' FROM timetable_entries");
        statement.execute("DROP TABLE timetable_entries");
        statement.execute("ALTER TABLE timetable_entries_v2 RENAME TO timetable_entries");
        statement.execute("CREATE INDEX IF NOT EXISTS idx_tt_entries_timetable ON timetable_entries(timetable_id)");
        statement.execute("CREATE INDEX IF NOT EXISTS idx_tt_entries_slot ON timetable_entries(timetable_id, day_of_week, period_number)");
        statement.execute("CREATE TABLE IF NOT EXISTS teacher_availability_backup AS SELECT * FROM teacher_availability");
        statement.execute("""
                CREATE TABLE teacher_availability_v2 (
                    id            INTEGER PRIMARY KEY AUTOINCREMENT,
                    teacher_id    INTEGER NOT NULL REFERENCES teachers(id) ON DELETE CASCADE,
                    day_of_week   TEXT    NOT NULL,
                    period_number INTEGER NOT NULL CHECK (period_number > 0),
                    available     INTEGER NOT NULL DEFAULT 1 CHECK (available IN (0, 1)),
                    UNIQUE (teacher_id, day_of_week, period_number)
                )
                """);
        statement.execute("INSERT INTO teacher_availability_v2 (teacher_id, day_of_week, period_number, available) SELECT teacher_id, day_of_week, period_number + 1, available FROM teacher_availability");
        statement.execute("DROP TABLE teacher_availability");
        statement.execute("ALTER TABLE teacher_availability_v2 RENAME TO teacher_availability");
    }

    private void runMigrationV10(Statement statement) throws SQLException {
        try {
            statement.execute("ALTER TABLE periods ADD COLUMN type TEXT NOT NULL DEFAULT 'LESSON' CHECK (type IN ('LESSON', 'BREAK'))");
        } catch (SQLException e) {
            if (!e.getMessage().contains("duplicate column")) throw e;
        }
        try {
            statement.execute("ALTER TABLE periods ADD COLUMN break_id INTEGER REFERENCES breaks(id) ON DELETE SET NULL");
        } catch (SQLException e) {
            if (!e.getMessage().contains("duplicate column")) throw e;
        }
    }

    private void runMigrationV11(Statement statement) throws SQLException {
        try {
            statement.execute("ALTER TABLE teaching_assignments ADD COLUMN duration TEXT NOT NULL DEFAULT 'SINGLE' CHECK (duration IN ('SINGLE', 'DOUBLE'))");
        } catch (SQLException e) {
            if (!e.getMessage().contains("duplicate column")) throw e;
        }
    }

    private void runMigrationV12(Statement statement) throws SQLException {
        try {
            statement.execute("ALTER TABLE subjects ADD COLUMN cbc_subject INTEGER NOT NULL DEFAULT 1 CHECK (cbc_subject IN (0, 1))");
            statement.execute("UPDATE subjects SET cbc_subject = examinable");
        } catch (SQLException e) {
            if (!e.getMessage().contains("duplicate column")) throw e;
        }
    }

    private void runMigrationV13(Statement statement) throws SQLException {
        statement.execute("""
                CREATE TABLE IF NOT EXISTS teacher_subjects (
                    id         INTEGER PRIMARY KEY AUTOINCREMENT,
                    teacher_id INTEGER NOT NULL REFERENCES teachers(id) ON DELETE CASCADE,
                    subject_id INTEGER NOT NULL REFERENCES subjects(id) ON DELETE CASCADE,
                    UNIQUE (teacher_id, subject_id)
                )
                """);
        statement.execute("CREATE INDEX IF NOT EXISTS idx_ts_teacher ON teacher_subjects(teacher_id)");
        statement.execute("CREATE INDEX IF NOT EXISTS idx_ts_subject ON teacher_subjects(subject_id)");
    }

    private void runMigrationV14(Statement statement) throws SQLException {
        try {
            statement.execute("ALTER TABLE school_settings ADD COLUMN spread_weight REAL NOT NULL DEFAULT 0.50");
        } catch (SQLException e) {
            if (!e.getMessage().contains("duplicate column")) throw e;
        }
        try {
            statement.execute("ALTER TABLE school_settings ADD COLUMN consecutive_weight REAL NOT NULL DEFAULT 0.40");
        } catch (SQLException e) {
            if (!e.getMessage().contains("duplicate column")) throw e;
        }
        try {
            statement.execute("ALTER TABLE school_settings ADD COLUMN balance_weight REAL NOT NULL DEFAULT 0.10");
        } catch (SQLException e) {
            if (!e.getMessage().contains("duplicate column")) throw e;
        }
    }

    private void seedDefaults() {
        try (Connection connection = connectionProvider.getConnection();
             Statement statement = connection.createStatement()) {

            var settingsCount = connection.createStatement()
                    .executeQuery("SELECT COUNT(*) FROM school_settings");
            if (settingsCount.next() && settingsCount.getInt(1) == 0) {
                statement.execute("INSERT INTO school_settings (id, total_periods, school_start_time, school_end_time, period_duration_min) VALUES (1, 15, '07:00', '18:25', 40)");
            }

            var breakCount = connection.createStatement()
                    .executeQuery("SELECT COUNT(*) FROM breaks");
            if (breakCount.next() && breakCount.getInt(1) == 0) {
                statement.execute("INSERT INTO breaks (name, after_period, duration_minutes, sort_order, is_before_period_one, slotable, start_time, end_time) VALUES ('Assembly', 0, 50, 1, 1, 1, '07:00', '07:50')");
                statement.execute("INSERT INTO breaks (name, after_period, duration_minutes, sort_order, is_before_period_one, slotable, start_time, end_time) VALUES ('Tea Break', 3, 20, 2, 0, 0, '09:50', '10:10')");
                statement.execute("INSERT INTO breaks (name, after_period, duration_minutes, sort_order, is_before_period_one, slotable, start_time, end_time) VALUES ('Short Break', 5, 10, 3, 0, 0, '11:20', '11:30')");
                statement.execute("INSERT INTO breaks (name, after_period, duration_minutes, sort_order, is_before_period_one, slotable, start_time, end_time) VALUES ('Lunch Break', 7, 50, 4, 0, 0, '12:50', '13:40')");
                statement.execute("INSERT INTO breaks (name, after_period, duration_minutes, sort_order, is_before_period_one, slotable, start_time, end_time) VALUES ('Games Time', 10, 165, 5, 0, 0, '16:00', '18:45')");
            }

            var periodCount = connection.createStatement()
                    .executeQuery("SELECT COUNT(*) FROM periods");
            if (periodCount.next() && periodCount.getInt(1) == 0) {
                statement.execute("INSERT INTO periods (period_number, start_time, end_time, label, type, break_id) VALUES (1, '07:00', '07:50', 'Assembly', 'BREAK', 1)");
                statement.execute("INSERT INTO periods (period_number, start_time, end_time, label, type, break_id) VALUES (2, '07:50', '08:30', 'P1', 'LESSON', NULL)");
                statement.execute("INSERT INTO periods (period_number, start_time, end_time, label, type, break_id) VALUES (3, '08:30', '09:10', 'P2', 'LESSON', NULL)");
                statement.execute("INSERT INTO periods (period_number, start_time, end_time, label, type, break_id) VALUES (4, '09:10', '09:50', 'P3', 'LESSON', NULL)");
                statement.execute("INSERT INTO periods (period_number, start_time, end_time, label, type, break_id) VALUES (5, '09:50', '10:10', 'Tea Break', 'BREAK', 2)");
                statement.execute("INSERT INTO periods (period_number, start_time, end_time, label, type, break_id) VALUES (6, '10:10', '10:50', 'P4', 'LESSON', NULL)");
                statement.execute("INSERT INTO periods (period_number, start_time, end_time, label, type, break_id) VALUES (7, '10:50', '11:20', 'P5', 'LESSON', NULL)");
                statement.execute("INSERT INTO periods (period_number, start_time, end_time, label, type, break_id) VALUES (8, '11:20', '11:30', 'Short Break', 'BREAK', 3)");
                statement.execute("INSERT INTO periods (period_number, start_time, end_time, label, type, break_id) VALUES (9, '11:30', '12:10', 'P6', 'LESSON', NULL)");
                statement.execute("INSERT INTO periods (period_number, start_time, end_time, label, type, break_id) VALUES (10, '12:10', '12:50', 'P7', 'LESSON', NULL)");
                statement.execute("INSERT INTO periods (period_number, start_time, end_time, label, type, break_id) VALUES (11, '12:50', '13:40', 'Lunch Break', 'BREAK', 4)");
                statement.execute("INSERT INTO periods (period_number, start_time, end_time, label, type, break_id) VALUES (12, '13:40', '14:20', 'P8', 'LESSON', NULL)");
                statement.execute("INSERT INTO periods (period_number, start_time, end_time, label, type, break_id) VALUES (13, '14:20', '15:00', 'P9', 'LESSON', NULL)");
                statement.execute("INSERT INTO periods (period_number, start_time, end_time, label, type, break_id) VALUES (14, '15:00', '15:40', 'P10', 'LESSON', NULL)");
                statement.execute("INSERT INTO periods (period_number, start_time, end_time, label, type, break_id) VALUES (15, '15:40', '18:25', 'Games Time', 'BREAK', 5)"); // 165 min
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
