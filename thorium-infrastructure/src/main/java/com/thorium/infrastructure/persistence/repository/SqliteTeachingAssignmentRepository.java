package com.thorium.infrastructure.persistence.repository;

import com.thorium.application.port.TeachingAssignmentRepository;
import com.thorium.domain.model.LessonDuration;
import com.thorium.domain.model.TeachingAssignment;
import com.thorium.infrastructure.persistence.SQLiteConnectionProvider;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class SqliteTeachingAssignmentRepository extends AbstractRepository implements TeachingAssignmentRepository {

    public SqliteTeachingAssignmentRepository(SQLiteConnectionProvider connectionProvider) {
        super(connectionProvider);
    }

    @Override
    public TeachingAssignment save(TeachingAssignment assignment) {
        try (Connection conn = connection()) {
            if (assignment.getId() == null) {
                insert(conn, assignment);
            } else {
                update(conn, assignment);
            }
            commit(conn);
            return assignment;
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to save teaching assignment", e);
        }
    }

    private void insert(Connection conn, TeachingAssignment a) throws SQLException {
        String sql = """
                INSERT INTO teaching_assignments (teacher_id, subject_id, class_stream_id, lessons_per_week, duration)
                VALUES (?, ?, ?, ?, ?)
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bind(ps, a);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    a.setId(keys.getLong(1));
                }
            }
        }
    }

    private void update(Connection conn, TeachingAssignment a) throws SQLException {
        String sql = """
                UPDATE teaching_assignments SET teacher_id=?, subject_id=?, class_stream_id=?, lessons_per_week=?, duration=?
                WHERE id=?
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            bind(ps, a);
            ps.setLong(6, a.getId());
            ps.executeUpdate();
        }
    }

    private void bind(PreparedStatement ps, TeachingAssignment a) throws SQLException {
        ps.setLong(1, a.getTeacherId());
        ps.setLong(2, a.getSubjectId());
        ps.setLong(3, a.getClassStreamId());
        ps.setInt(4, a.getLessonsPerWeek());
        ps.setString(5, a.getDuration().name());
    }

    @Override
    public Optional<TeachingAssignment> findById(Long id) {
        try (Connection conn = connection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM teaching_assignments WHERE id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to find assignment", e);
        }
    }

    @Override
    public List<TeachingAssignment> findAll() {
        try (Connection conn = connection();
             ResultSet rs = conn.createStatement().executeQuery("SELECT * FROM teaching_assignments ORDER BY id")) {
            List<TeachingAssignment> list = new ArrayList<>();
            while (rs.next()) {
                list.add(map(rs));
            }
            return list;
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to list assignments", e);
        }
    }

    @Override
    public List<TeachingAssignment> findByTeacherId(Long teacherId) {
        try (Connection conn = connection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT * FROM teaching_assignments WHERE teacher_id = ? ORDER BY id")) {
            ps.setLong(1, teacherId);
            try (ResultSet rs = ps.executeQuery()) {
                List<TeachingAssignment> list = new ArrayList<>();
                while (rs.next()) {
                    list.add(map(rs));
                }
                return list;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to find assignments by teacher", e);
        }
    }

    @Override
    public List<TeachingAssignment> findByClassStreamId(Long classStreamId) {
        try (Connection conn = connection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT * FROM teaching_assignments WHERE class_stream_id = ? ORDER BY id")) {
            ps.setLong(1, classStreamId);
            try (ResultSet rs = ps.executeQuery()) {
                List<TeachingAssignment> list = new ArrayList<>();
                while (rs.next()) {
                    list.add(map(rs));
                }
                return list;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to find assignments by class stream", e);
        }
    }

    @Override
    public void deleteById(Long id) {
        try (Connection conn = connection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM teaching_assignments WHERE id = ?")) {
            ps.setLong(1, id);
            ps.executeUpdate();
            commit(conn);
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to delete assignment", e);
        }
    }

    @Override
    public long count() {
        try (Connection conn = connection();
             ResultSet rs = conn.createStatement().executeQuery("SELECT COUNT(*) FROM teaching_assignments")) {
            return rs.next() ? rs.getLong(1) : 0;
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to count assignments", e);
        }
    }

    private TeachingAssignment map(ResultSet rs) throws SQLException {
        LessonDuration duration;
        try {
            duration = LessonDuration.valueOf(rs.getString("duration"));
        } catch (Exception e) {
            duration = LessonDuration.SINGLE;
        }
        return new TeachingAssignment(
                rs.getLong("id"),
                rs.getLong("teacher_id"),
                rs.getLong("subject_id"),
                rs.getLong("class_stream_id"),
                rs.getInt("lessons_per_week"),
                duration
        );
    }
}
