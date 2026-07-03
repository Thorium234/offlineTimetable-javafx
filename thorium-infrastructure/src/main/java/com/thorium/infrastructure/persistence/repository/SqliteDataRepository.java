package com.thorium.infrastructure.persistence.repository;

import com.thorium.application.port.DataRepository;
import com.thorium.infrastructure.persistence.SQLiteConnectionProvider;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public class SqliteDataRepository extends AbstractRepository implements DataRepository {

    private static final Logger LOG = Logger.getLogger(SqliteDataRepository.class.getName());

    public SqliteDataRepository(SQLiteConnectionProvider connectionProvider) {
        super(connectionProvider);
    }

    @Override
    public void clearAllData() {
        executeWithRollbackVoid(conn -> {
            disableForeignKeys(conn);
            deleteAll(conn, "timetable_entries");
            deleteAll(conn, "timetables");
            deleteAll(conn, "teacher_availability");
            deleteAll(conn, "teaching_assignments");
            deleteAll(conn, "teacher_subjects");
            deleteAll(conn, "teachers");
            deleteAll(conn, "subjects");
            deleteAll(conn, "class_streams");
            deleteAll(conn, "rooms");
            enableForeignKeys(conn);
        });
        LOG.info("All user data cleared successfully");
    }

    @Override
    public List<Long> generateSampleData() {
        return executeWithRollback(conn -> {
            disableForeignKeys(conn);

            List<Long> classIds = insertClasses(conn);
            List<Long> subjectIds = insertSubjects(conn);
            List<Long> teacherIds = insertTeachers(conn);
            insertTeacherSubjects(conn, teacherIds, subjectIds);
            List<Long> assignmentIds = insertAssignments(conn, classIds, subjectIds, teacherIds);

            enableForeignKeys(conn);
            LOG.info("Sample data generated: " + classIds.size() + " classes, "
                    + subjectIds.size() + " subjects, " + teacherIds.size()
                    + " teachers, " + assignmentIds.size() + " assignments");
            return assignmentIds;
        });
    }

    private void disableForeignKeys(Connection conn) throws SQLException {
        try (var stmt = conn.createStatement()) {
            stmt.execute("PRAGMA foreign_keys = OFF");
        }
    }

    private void enableForeignKeys(Connection conn) throws SQLException {
        try (var stmt = conn.createStatement()) {
            stmt.execute("PRAGMA foreign_keys = ON");
        }
    }

    private void deleteAll(Connection conn, String table) throws SQLException {
        try (var stmt = conn.createStatement()) {
            stmt.execute("DELETE FROM " + table);
        }
    }

    private List<Long> insertClasses(Connection conn) throws SQLException {
        List<Long> ids = new ArrayList<>();
        String[][] data = {
            {"F1E", "1", "East", "Form 1 East"},
            {"F1W", "1", "West", "Form 1 West"},
            {"F2E", "2", "East", "Form 2 East"},
            {"F2W", "2", "West", "Form 2 West"},
            {"F3E", "3", "East", "Form 3 East"},
            {"F3W", "3", "West", "Form 3 West"},
            {"F4E", "4", "East", "Form 4 East"},
            {"F4W", "4", "West", "Form 4 West"},
        };
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO class_streams (code, form, stream, display_name) VALUES (?, ?, ?, ?)")) {
            for (String[] row : data) {
                ps.setString(1, row[0]);
                ps.setInt(2, Integer.parseInt(row[1]));
                ps.setString(3, row[2]);
                ps.setString(4, row[3]);
                ps.executeUpdate();
                ids.add(lastId(conn));
            }
        }
        return ids;
    }

    private List<Long> insertSubjects(Connection conn) throws SQLException {
        List<Long> ids = new ArrayList<>();
        String[][] data = {
            {"ENG", "English", "true", "true", "5", "false", "false", "#3b82f6"},
            {"KISW", "Kiswahili", "true", "true", "5", "false", "false", "#10b981"},
            {"MATH", "Mathematics", "true", "true", "5", "false", "false", "#f59e0b"},
            {"BIO", "Biology", "true", "true", "5", "true", "false", "#ef4444"},
            {"CHEM", "Chemistry", "true", "true", "5", "true", "false", "#8b5cf6"},
            {"PHY", "Physics", "true", "true", "5", "true", "false", "#6366f1"},
            {"HIST", "History & Government", "true", "true", "4", "false", "false", "#14b8a6"},
            {"GEO", "Geography", "true", "true", "4", "false", "false", "#f97316"},
            {"CRE", "CRE", "true", "true", "4", "false", "false", "#e11d48"},
            {"COMP", "Computer Studies", "false", "true", "4", "true", "false", "#64748b"},
        };
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO subjects (code, name, examinable, cbc_subject, cbc_default_lessons, "
                + "allows_double_period, requires_double_period, color) VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
            for (String[] row : data) {
                ps.setString(1, row[0]);
                ps.setString(2, row[1]);
                ps.setBoolean(3, Boolean.parseBoolean(row[2]));
                ps.setBoolean(4, Boolean.parseBoolean(row[3]));
                ps.setInt(5, Integer.parseInt(row[4]));
                ps.setBoolean(6, Boolean.parseBoolean(row[5]));
                ps.setBoolean(7, Boolean.parseBoolean(row[6]));
                ps.setString(8, row[7]);
                ps.executeUpdate();
                ids.add(lastId(conn));
            }
        }
        return ids;
    }

    private List<Long> insertTeachers(Connection conn) throws SQLException {
        List<Long> ids = new ArrayList<>();
        String[][] data = {
            {"T001", "Alice Kamau"},
            {"T002", "Brian Ochieng"},
            {"T003", "Caroline Wanjiku"},
            {"T004", "David Mwangi"},
            {"T005", "Esther Akinyi"},
            {"T006", "Francis Njoroge"},
            {"T007", "Grace Chebet"},
            {"T008", "Henry Kiprop"},
            {"T009", "Irene Nyambura"},
            {"T010", "James Otieno"},
            {"T011", "Katherine Wambui"},
            {"T012", "Lawrence Kiplagat"},
            {"T013", "Monica Atieno"},
            {"T014", "Nicholas Mutua"},
            {"T015", "Olivia Nyakio"},
            {"T016", "Patrick Muthomi"},
        };
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO teachers (code, name, active, max_lessons_per_day, max_lessons_per_week) "
                + "VALUES (?, ?, 1, 10, 50)")) {
            for (String[] row : data) {
                ps.setString(1, row[0]);
                ps.setString(2, row[1]);
                ps.executeUpdate();
                ids.add(lastId(conn));
            }
        }
        return ids;
    }

    private void insertTeacherSubjects(Connection conn, List<Long> teacherIds, List<Long> subjectIds) throws SQLException {
        Long eng = subjectIds.get(0), kisw = subjectIds.get(1), math = subjectIds.get(2);
        Long bio = subjectIds.get(3), chem = subjectIds.get(4), phy = subjectIds.get(5);
        Long hist = subjectIds.get(6), geo = subjectIds.get(7), cre = subjectIds.get(8), comp = subjectIds.get(9);

        long[][] mapping = {
            {teacherIds.get(0), eng},
            {teacherIds.get(0), hist},
            {teacherIds.get(1), kisw},
            {teacherIds.get(2), math},
            {teacherIds.get(3), bio},
            {teacherIds.get(3), chem},
            {teacherIds.get(4), phy},
            {teacherIds.get(5), eng},
            {teacherIds.get(5), cre},
            {teacherIds.get(6), kisw},
            {teacherIds.get(7), math},
            {teacherIds.get(8), bio},
            {teacherIds.get(9), chem},
            {teacherIds.get(9), phy},
            {teacherIds.get(10), hist},
            {teacherIds.get(10), geo},
            {teacherIds.get(11), geo},
            {teacherIds.get(11), cre},
            {teacherIds.get(12), eng},
            {teacherIds.get(12), comp},
            {teacherIds.get(13), kisw},
            {teacherIds.get(13), comp},
            {teacherIds.get(14), math},
            {teacherIds.get(15), bio},
            {teacherIds.get(15), chem},
        };
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO teacher_subjects (teacher_id, subject_id) VALUES (?, ?)")) {
            for (long[] row : mapping) {
                ps.setLong(1, row[0]);
                ps.setLong(2, row[1]);
                ps.executeUpdate();
            }
        }
    }

    private List<Long> insertAssignments(Connection conn, List<Long> classIds,
                                          List<Long> subjectIds, List<Long> teacherIds) throws SQLException {
        List<Long> ids = new ArrayList<>();
        Long eng = subjectIds.get(0), kisw = subjectIds.get(1), math = subjectIds.get(2);
        Long bio = subjectIds.get(3), chem = subjectIds.get(4), phy = subjectIds.get(5);
        Long hist = subjectIds.get(6), geo = subjectIds.get(7), cre = subjectIds.get(8), comp = subjectIds.get(9);

        long[][] subjectTeachers = {
            {eng, teacherIds.get(0)},
            {kisw, teacherIds.get(1)},
            {math, teacherIds.get(2)},
            {bio, teacherIds.get(3)},
            {chem, teacherIds.get(3)},
            {phy, teacherIds.get(4)},
            {hist, teacherIds.get(0)},
            {geo, teacherIds.get(10)},
            {cre, teacherIds.get(5)},
            {comp, teacherIds.get(12)},
        };
        int[] lessonsPerWeek = {5, 5, 5, 5, 5, 5, 4, 4, 4, 4};
        String[] durations = {"SINGLE", "SINGLE", "SINGLE", "DOUBLE", "DOUBLE", "DOUBLE", "SINGLE", "SINGLE", "SINGLE", "DOUBLE"};

        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO teaching_assignments (teacher_id, subject_id, class_stream_id, lessons_per_week, duration) "
                + "VALUES (?, ?, ?, ?, ?)")) {
            for (Long classId : classIds) {
                for (int s = 0; s < subjectTeachers.length; s++) {
                    ps.setLong(1, subjectTeachers[s][1]);
                    ps.setLong(2, subjectTeachers[s][0]);
                    ps.setLong(3, classId);
                    ps.setInt(4, lessonsPerWeek[s]);
                    ps.setString(5, durations[s]);
                    ps.executeUpdate();
                    ids.add(lastId(conn));
                }
            }
        }
        return ids;
    }

    private long lastId(Connection conn) throws SQLException {
        try (var rs = conn.createStatement().executeQuery("SELECT last_insert_rowid()")) {
            rs.next();
            return rs.getLong(1);
        }
    }
}
