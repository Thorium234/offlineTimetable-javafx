package com.thorium.infrastructure.persistence.repository;

import com.thorium.application.port.SubjectRepository;
import com.thorium.domain.model.Subject;
import com.thorium.infrastructure.persistence.SQLiteConnectionProvider;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class SqliteSubjectRepository extends AbstractRepository implements SubjectRepository {

    public SqliteSubjectRepository(SQLiteConnectionProvider connectionProvider) {
        super(connectionProvider);
    }

    @Override
    public Subject save(Subject subject) {
        try (Connection conn = connection()) {
            if (subject.getId() == null) {
                insert(conn, subject);
            } else {
                update(conn, subject);
            }
            commit(conn);
            return subject;
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to save subject", e);
        }
    }

    private void insert(Connection conn, Subject subject) throws SQLException {
        String sql = """
                INSERT INTO subjects (code, name, examinable, cbc_default_lessons, allows_double_period, requires_double_period)
                VALUES (?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bind(ps, subject);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    subject.setId(keys.getLong(1));
                }
            }
        }
    }

    private void update(Connection conn, Subject subject) throws SQLException {
        String sql = """
                UPDATE subjects SET code=?, name=?, examinable=?, cbc_default_lessons=?, allows_double_period=?, requires_double_period=?
                WHERE id=?
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            bind(ps, subject);
            ps.setLong(7, subject.getId());
            ps.executeUpdate();
        }
    }

    private void bind(PreparedStatement ps, Subject subject) throws SQLException {
        ps.setString(1, subject.getCode());
        ps.setString(2, subject.getName());
        ps.setInt(3, subject.isExaminable() ? 1 : 0);
        ps.setInt(4, subject.getCbcDefaultLessons());
        ps.setInt(5, subject.isAllowsDoublePeriod() ? 1 : 0);
        ps.setInt(6, subject.isRequiresDoublePeriod() ? 1 : 0);
    }

    @Override
    public Optional<Subject> findById(Long id) {
        try (Connection conn = connection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM subjects WHERE id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to find subject", e);
        }
    }

    @Override
    public List<Subject> findAll() {
        try (Connection conn = connection();
             ResultSet rs = conn.createStatement().executeQuery("SELECT * FROM subjects ORDER BY name")) {
            List<Subject> list = new ArrayList<>();
            while (rs.next()) {
                list.add(map(rs));
            }
            return list;
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to list subjects", e);
        }
    }

    @Override
    public void deleteById(Long id) {
        try (Connection conn = connection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM subjects WHERE id = ?")) {
            ps.setLong(1, id);
            ps.executeUpdate();
            commit(conn);
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to delete subject", e);
        }
    }

    @Override
    public long count() {
        try (Connection conn = connection();
             ResultSet rs = conn.createStatement().executeQuery("SELECT COUNT(*) FROM subjects")) {
            return rs.next() ? rs.getLong(1) : 0;
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to count subjects", e);
        }
    }

    private Subject map(ResultSet rs) throws SQLException {
        Subject subject = new Subject(
                rs.getLong("id"),
                rs.getString("code"),
                rs.getString("name"),
                rs.getInt("examinable") == 1,
                rs.getInt("cbc_default_lessons"),
                rs.getInt("allows_double_period") == 1,
                rs.getInt("requires_double_period") == 1
        );
        return subject;
    }
}
