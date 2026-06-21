-- Thorium Timetable Generator — SQLite Schema
PRAGMA foreign_keys = ON;

CREATE TABLE IF NOT EXISTS teachers (
    id                  INTEGER PRIMARY KEY AUTOINCREMENT,
    code                TEXT    NOT NULL UNIQUE,
    name                TEXT    NOT NULL,
    max_lessons_per_day INTEGER NOT NULL DEFAULT 6,
    max_lessons_per_week INTEGER NOT NULL DEFAULT 30,
    active              INTEGER NOT NULL DEFAULT 1 CHECK (active IN (0, 1))
);

CREATE TABLE IF NOT EXISTS subjects (
    id                   INTEGER PRIMARY KEY AUTOINCREMENT,
    code                 TEXT    NOT NULL UNIQUE,
    name                 TEXT    NOT NULL,
    examinable           INTEGER NOT NULL DEFAULT 0 CHECK (examinable IN (0, 1)),
    cbc_default_lessons  INTEGER NOT NULL DEFAULT 5,
    allows_double_period  INTEGER NOT NULL DEFAULT 0 CHECK (allows_double_period IN (0, 1)),
    requires_double_period INTEGER NOT NULL DEFAULT 0 CHECK (requires_double_period IN (0, 1))
);

CREATE TABLE IF NOT EXISTS class_streams (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    code         TEXT    NOT NULL UNIQUE,
    form         INTEGER NOT NULL,
    stream       TEXT    NOT NULL,
    display_name TEXT    NOT NULL
);

CREATE TABLE IF NOT EXISTS teaching_assignments (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    teacher_id      INTEGER NOT NULL REFERENCES teachers(id) ON DELETE CASCADE,
    subject_id      INTEGER NOT NULL REFERENCES subjects(id) ON DELETE CASCADE,
    class_stream_id INTEGER NOT NULL REFERENCES class_streams(id) ON DELETE CASCADE,
    lessons_per_week INTEGER NOT NULL CHECK (lessons_per_week > 0),
    UNIQUE (teacher_id, subject_id, class_stream_id)
);

CREATE TABLE IF NOT EXISTS teacher_availability (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    teacher_id    INTEGER NOT NULL REFERENCES teachers(id) ON DELETE CASCADE,
    day_of_week   TEXT    NOT NULL,
    period_number INTEGER NOT NULL CHECK (period_number > 0),
    available     INTEGER NOT NULL DEFAULT 1 CHECK (available IN (0, 1)),
    UNIQUE (teacher_id, day_of_week, period_number)
);

CREATE TABLE IF NOT EXISTS periods (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    period_number INTEGER NOT NULL UNIQUE CHECK (period_number > 0),
    start_time    TEXT    NOT NULL,
    end_time      TEXT    NOT NULL,
    label         TEXT    NOT NULL
);

CREATE TABLE IF NOT EXISTS breaks (
    id               INTEGER PRIMARY KEY AUTOINCREMENT,
    name             TEXT    NOT NULL,
    after_period     INTEGER NOT NULL DEFAULT 0,
    duration_minutes INTEGER NOT NULL CHECK (duration_minutes > 0),
    sort_order       INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS constraints (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    constraint_type TEXT    NOT NULL UNIQUE,
    enabled         INTEGER NOT NULL DEFAULT 1 CHECK (enabled IN (0, 1)),
    parameters      TEXT
);

CREATE TABLE IF NOT EXISTS timetables (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    name          TEXT    NOT NULL,
    status        TEXT    NOT NULL DEFAULT 'DRAFT',
    created_at    TEXT    NOT NULL,
    quality_score REAL    DEFAULT 0.0
);

CREATE TABLE IF NOT EXISTS rooms (
    id       INTEGER PRIMARY KEY AUTOINCREMENT,
    code     TEXT    NOT NULL UNIQUE,
    name     TEXT    NOT NULL,
    type     TEXT    NOT NULL DEFAULT 'CLASSROOM' CHECK (type IN ('CLASSROOM', 'LAB')),
    capacity INTEGER NOT NULL DEFAULT 30 CHECK (capacity > 0)
);

CREATE TABLE IF NOT EXISTS timetable_entries (
    id                     INTEGER PRIMARY KEY AUTOINCREMENT,
    timetable_id           INTEGER NOT NULL REFERENCES timetables(id) ON DELETE CASCADE,
    teaching_assignment_id INTEGER NOT NULL REFERENCES teaching_assignments(id) ON DELETE CASCADE,
    day_of_week            TEXT    NOT NULL,
    period_number          INTEGER NOT NULL CHECK (period_number > 0),
    room_id                INTEGER REFERENCES rooms(id) ON DELETE SET NULL,
    UNIQUE (timetable_id, teaching_assignment_id, day_of_week, period_number)
);

CREATE INDEX IF NOT EXISTS idx_ta_teacher ON teaching_assignments(teacher_id);
CREATE INDEX IF NOT EXISTS idx_ta_class ON teaching_assignments(class_stream_id);
CREATE INDEX IF NOT EXISTS idx_teacher_avail ON teacher_availability(teacher_id, day_of_week, period_number);
CREATE INDEX IF NOT EXISTS idx_tt_entries_timetable ON timetable_entries(timetable_id);
CREATE INDEX IF NOT EXISTS idx_tt_entries_slot ON timetable_entries(timetable_id, day_of_week, period_number);
