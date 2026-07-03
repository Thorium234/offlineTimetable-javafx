package com.thorium.infrastructure.persistence.repository;

import com.thorium.application.port.TeacherRepository;
import com.thorium.domain.model.Teacher;
import com.thorium.infrastructure.persistence.SQLiteConnectionProvider;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class SqliteTeacherRepository extends AbstractRepository implements TeacherRepository {

    public SqliteTeacherRepository(SQLiteConnectionProvider connectionProvider) {
        super(connectionProvider);
    }

    @Override
    public Teacher save(Teacher teacher) {
        try (Connection conn = connection()) {
            Teacher saved;
            if (teacher.getId() == null) {
                saved = insert(conn, teacher);
            } else {
                saved = update(conn, teacher);
            }
            commit(conn);
            return saved;
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to save teacher", e);
        }
    }

    private Teacher insert(Connection conn, Teacher teacher) throws SQLException {
        String sql = """
                INSERT INTO teachers (code, name, max_lessons_per_day, max_lessons_per_week, active)
                VALUES (?, ?, ?, ?, ?)
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bind(ps, teacher);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    teacher.setId(keys.getLong(1));
                }
            }
            return teacher;
        }
    }

    private Teacher update(Connection conn, Teacher teacher) throws SQLException {
        String sql = """
                UPDATE teachers SET code=?, name=?, max_lessons_per_day=?, max_lessons_per_week=?, active=?
                WHERE id=?
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            bind(ps, teacher);
            ps.setLong(6, teacher.getId());
            ps.executeUpdate();
            return teacher;
        }
    }

    private void bind(PreparedStatement ps, Teacher teacher) throws SQLException {
        ps.setString(1, teacher.getCode());
        ps.setString(2, teacher.getName());
        ps.setInt(3, teacher.getMaxLessonsPerDay());
        ps.setInt(4, teacher.getMaxLessonsPerWeek());
        ps.setInt(5, teacher.isActive() ? 1 : 0);
    }

    @Override
    public Optional<Teacher> findById(Long id) {
        return findOne("SELECT * FROM teachers WHERE id = ?", id);
    }

    @Override
    public Optional<Teacher> findByCode(String code) {
        return findOne("SELECT * FROM teachers WHERE code = ?", code);
    }

    private Optional<Teacher> findOne(String sql, Object param) {
        try (Connection conn = connection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, param);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to find teacher", e);
        }
    }

    @Override
    public List<Teacher> findAll() {
        try (Connection conn = connection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM teachers ORDER BY name")) {
            List<Teacher> teachers = new ArrayList<>();
            while (rs.next()) {
                teachers.add(map(rs));
            }
            return teachers;
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to list teachers", e);
        }
    }

    @Override
    public void deleteById(Long id) {
        try (Connection conn = connection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM teachers WHERE id = ?")) {
            ps.setLong(1, id);
            ps.executeUpdate();
            commit(conn);
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to delete teacher", e);
        }
    }

    @Override
    public long count() {
        try (Connection conn = connection();
             ResultSet rs = conn.createStatement().executeQuery("SELECT COUNT(*) FROM teachers")) {
            return rs.next() ? rs.getLong(1) : 0;
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to count teachers", e);
        }
    }

    private Teacher map(ResultSet rs) throws SQLException {
        return new Teacher(
                rs.getLong("id"),
                rs.getString("code"),
                rs.getString("name"),
                rs.getInt("active") == 1,
                rs.getInt("max_lessons_per_day"),
                rs.getInt("max_lessons_per_week")
        );
    }
}
