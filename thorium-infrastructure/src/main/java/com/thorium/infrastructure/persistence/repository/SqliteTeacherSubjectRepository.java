package com.thorium.infrastructure.persistence.repository;

import com.thorium.application.port.TeacherSubjectRepository;
import com.thorium.domain.model.TeacherSubject;
import com.thorium.infrastructure.persistence.SQLiteConnectionProvider;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class SqliteTeacherSubjectRepository extends AbstractRepository implements TeacherSubjectRepository {

    public SqliteTeacherSubjectRepository(SQLiteConnectionProvider connectionProvider) {
        super(connectionProvider);
    }

    @Override
    public TeacherSubject save(TeacherSubject teacherSubject) {
        try (Connection conn = connection()) {
            if (teacherSubject.getId() == null) {
                insert(conn, teacherSubject);
            } else {
                update(conn, teacherSubject);
            }
            commit(conn);
            return teacherSubject;
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to save teacher-subject", e);
        }
    }

    private void insert(Connection conn, TeacherSubject ts) throws SQLException {
        String sql = "INSERT INTO teacher_subjects (teacher_id, subject_id) VALUES (?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, ts.getTeacherId());
            ps.setLong(2, ts.getSubjectId());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    ts.setId(keys.getLong(1));
                }
            }
        }
    }

    private void update(Connection conn, TeacherSubject ts) throws SQLException {
        String sql = "UPDATE teacher_subjects SET teacher_id=?, subject_id=? WHERE id=?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, ts.getTeacherId());
            ps.setLong(2, ts.getSubjectId());
            ps.setLong(3, ts.getId());
            ps.executeUpdate();
        }
    }

    @Override
    public Optional<TeacherSubject> findById(Long id) {
        try (Connection conn = connection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM teacher_subjects WHERE id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to find teacher-subject", e);
        }
    }

    @Override
    public List<TeacherSubject> findAll() {
        try (Connection conn = connection();
             ResultSet rs = conn.createStatement().executeQuery("SELECT * FROM teacher_subjects ORDER BY id")) {
            List<TeacherSubject> list = new ArrayList<>();
            while (rs.next()) {
                list.add(map(rs));
            }
            return list;
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to list teacher-subjects", e);
        }
    }

    @Override
    public List<TeacherSubject> findByTeacherId(Long teacherId) {
        try (Connection conn = connection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT * FROM teacher_subjects WHERE teacher_id = ? ORDER BY id")) {
            ps.setLong(1, teacherId);
            try (ResultSet rs = ps.executeQuery()) {
                List<TeacherSubject> list = new ArrayList<>();
                while (rs.next()) {
                    list.add(map(rs));
                }
                return list;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to find teacher-subjects by teacher", e);
        }
    }

    @Override
    public List<TeacherSubject> findBySubjectId(Long subjectId) {
        try (Connection conn = connection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT * FROM teacher_subjects WHERE subject_id = ? ORDER BY id")) {
            ps.setLong(1, subjectId);
            try (ResultSet rs = ps.executeQuery()) {
                List<TeacherSubject> list = new ArrayList<>();
                while (rs.next()) {
                    list.add(map(rs));
                }
                return list;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to find teacher-subjects by subject", e);
        }
    }

    @Override
    public void deleteById(Long id) {
        try (Connection conn = connection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM teacher_subjects WHERE id = ?")) {
            ps.setLong(1, id);
            ps.executeUpdate();
            commit(conn);
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to delete teacher-subject", e);
        }
    }

    @Override
    public void deleteByTeacherIdAndSubjectId(Long teacherId, Long subjectId) {
        try (Connection conn = connection();
             PreparedStatement ps = conn.prepareStatement(
                     "DELETE FROM teacher_subjects WHERE teacher_id = ? AND subject_id = ?")) {
            ps.setLong(1, teacherId);
            ps.setLong(2, subjectId);
            ps.executeUpdate();
            commit(conn);
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to delete teacher-subject", e);
        }
    }

    @Override
    public long count() {
        try (Connection conn = connection();
             ResultSet rs = conn.createStatement().executeQuery("SELECT COUNT(*) FROM teacher_subjects")) {
            return rs.next() ? rs.getLong(1) : 0;
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to count teacher-subjects", e);
        }
    }

    private TeacherSubject map(ResultSet rs) throws SQLException {
        return new TeacherSubject(
                rs.getLong("id"),
                rs.getLong("teacher_id"),
                rs.getLong("subject_id")
        );
    }
}
