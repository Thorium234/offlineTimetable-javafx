package com.thorium.infrastructure.persistence.repository;

import com.thorium.application.port.TimetableRepository;
import com.thorium.domain.model.Timetable;
import com.thorium.domain.model.TimetableEntry;
import com.thorium.domain.value.DayOfWeek;
import com.thorium.domain.value.TimetableStatus;
import com.thorium.infrastructure.persistence.SQLiteConnectionProvider;

import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class SqliteTimetableRepository extends AbstractRepository implements TimetableRepository {

    private static final DateTimeFormatter DT_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    public SqliteTimetableRepository(SQLiteConnectionProvider connectionProvider) {
        super(connectionProvider);
    }

    @Override
    public Timetable save(Timetable timetable) {
        try (Connection conn = connection()) {
            if (timetable.getId() == null) {
                insert(conn, timetable);
            } else {
                update(conn, timetable);
            }
            commit(conn);
            return timetable;
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to save timetable", e);
        }
    }

    @Override
    public Timetable saveWithEntries(Timetable timetable, List<TimetableEntry> entries) {
        try (Connection conn = connection()) {
            if (timetable.getId() == null) {
                insert(conn, timetable);
            } else {
                update(conn, timetable);
                try (PreparedStatement delete = conn.prepareStatement("DELETE FROM timetable_entries WHERE timetable_id = ?")) {
                    delete.setLong(1, timetable.getId());
                    delete.executeUpdate();
                }
            }

            String entrySql = """
                    INSERT INTO timetable_entries (timetable_id, teaching_assignment_id, day_of_week, period_number, room_id, slot_type, break_id)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    """;
            try (PreparedStatement ps = conn.prepareStatement(entrySql, Statement.RETURN_GENERATED_KEYS)) {
                for (TimetableEntry entry : entries) {
                    ps.setLong(1, timetable.getId());
                    ps.setLong(2, entry.getTeachingAssignmentId());
                    ps.setString(3, entry.getDayOfWeek().name());
                    ps.setInt(4, entry.getPeriodNumber());
                    if (entry.getRoomId() != null) {
                        ps.setLong(5, entry.getRoomId());
                    } else {
                        ps.setNull(5, Types.INTEGER);
                    }
                    ps.setString(6, entry.getSlotType() != null ? entry.getSlotType() : "PERIOD");
                    if (entry.getBreakId() != null) {
                        ps.setLong(7, entry.getBreakId());
                    } else {
                        ps.setNull(7, Types.INTEGER);
                    }
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            commit(conn);
            return timetable;
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to save timetable with entries", e);
        }
    }

    private void insert(Connection conn, Timetable timetable) throws SQLException {
        String sql = "INSERT INTO timetables (name, status, created_at, quality_score) VALUES (?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, timetable.getName());
            ps.setString(2, timetable.getStatus().name());
            ps.setString(3, timetable.getCreatedAt().format(DT_FORMAT));
            ps.setDouble(4, timetable.getQualityScore());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    timetable.setId(keys.getLong(1));
                }
            }
        }
    }

    private void update(Connection conn, Timetable timetable) throws SQLException {
        String sql = "UPDATE timetables SET name=?, status=?, created_at=?, quality_score=? WHERE id=?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, timetable.getName());
            ps.setString(2, timetable.getStatus().name());
            ps.setString(3, timetable.getCreatedAt().format(DT_FORMAT));
            ps.setDouble(4, timetable.getQualityScore());
            ps.setLong(5, timetable.getId());
            ps.executeUpdate();
        }
    }

    @Override
    public Optional<Timetable> findByName(String name) {
        try (Connection conn = connection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM timetables WHERE name = ?")) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapTimetable(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to find timetable by name", e);
        }
    }

    @Override
    public Optional<Timetable> findById(Long id) {
        try (Connection conn = connection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM timetables WHERE id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapTimetable(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to find timetable", e);
        }
    }

    @Override
    public Optional<TimetableWithEntries> findByIdWithEntries(Long id) {
        Optional<Timetable> timetable = findById(id);
        if (timetable.isEmpty()) {
            return Optional.empty();
        }
        try (Connection conn = connection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT * FROM timetable_entries WHERE timetable_id = ? ORDER BY day_of_week, period_number")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                List<TimetableEntry> entries = new ArrayList<>();
                while (rs.next()) {
                    entries.add(mapEntry(rs));
                }
                return Optional.of(new TimetableWithEntries(timetable.get(), entries));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to find timetable entries", e);
        }
    }

    @Override
    public List<Timetable> findAll() {
        try (Connection conn = connection();
             ResultSet rs = conn.createStatement().executeQuery("SELECT * FROM timetables ORDER BY created_at")) {
            List<Timetable> list = new ArrayList<>();
            while (rs.next()) {
                list.add(mapTimetable(rs));
            }
            return list;
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to list timetables", e);
        }
    }

    @Override
    public void deleteEntry(Long entryId) {
        try (Connection conn = connection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM timetable_entries WHERE id = ?")) {
            ps.setLong(1, entryId);
            ps.executeUpdate();
            commit(conn);
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to delete timetable entry", e);
        }
    }

    @Override
    public TimetableEntry saveEntry(TimetableEntry entry) {
        try (Connection conn = connection()) {
            if (entry.getId() == null) {
                insertEntry(conn, entry);
            } else {
                updateEntry(conn, entry);
            }
            commit(conn);
            return entry;
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to save timetable entry", e);
        }
    }

    private void insertEntry(Connection conn, TimetableEntry entry) throws SQLException {
        String sql = """
                INSERT INTO timetable_entries (timetable_id, teaching_assignment_id, day_of_week, period_number, room_id, slot_type, break_id)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, entry.getTimetableId());
            ps.setLong(2, entry.getTeachingAssignmentId());
            ps.setString(3, entry.getDayOfWeek().name());
            ps.setInt(4, entry.getPeriodNumber());
            if (entry.getRoomId() != null) {
                ps.setLong(5, entry.getRoomId());
            } else {
                ps.setNull(5, Types.INTEGER);
            }
            ps.setString(6, entry.getSlotType() != null ? entry.getSlotType() : "PERIOD");
            if (entry.getBreakId() != null) {
                ps.setLong(7, entry.getBreakId());
            } else {
                ps.setNull(7, Types.INTEGER);
            }
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    entry.setId(keys.getLong(1));
                }
            }
        }
    }

    private void updateEntry(Connection conn, TimetableEntry entry) throws SQLException {
        String sql = """
                UPDATE timetable_entries
                SET teaching_assignment_id=?, day_of_week=?, period_number=?, room_id=?, slot_type=?, break_id=?
                WHERE id=?
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, entry.getTeachingAssignmentId());
            ps.setString(2, entry.getDayOfWeek().name());
            ps.setInt(3, entry.getPeriodNumber());
            if (entry.getRoomId() != null) {
                ps.setLong(4, entry.getRoomId());
            } else {
                ps.setNull(4, Types.INTEGER);
            }
            ps.setString(5, entry.getSlotType() != null ? entry.getSlotType() : "PERIOD");
            if (entry.getBreakId() != null) {
                ps.setLong(6, entry.getBreakId());
            } else {
                ps.setNull(6, Types.INTEGER);
            }
            ps.setLong(7, entry.getId());
            ps.executeUpdate();
        }
    }

    @Override
    public void deleteById(Long id) {
        try (Connection conn = connection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM timetables WHERE id = ?")) {
            ps.setLong(1, id);
            ps.executeUpdate();
            commit(conn);
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to delete timetable", e);
        }
    }

    private Timetable mapTimetable(ResultSet rs) throws SQLException {
        return new Timetable(
                rs.getLong("id"),
                rs.getString("name"),
                TimetableStatus.valueOf(rs.getString("status")),
                LocalDateTime.parse(rs.getString("created_at"), DT_FORMAT),
                rs.getDouble("quality_score")
        );
    }

    private TimetableEntry mapEntry(ResultSet rs) throws SQLException {
        TimetableEntry entry = new TimetableEntry();
        entry.setId(rs.getLong("id"));
        entry.setTimetableId(rs.getLong("timetable_id"));
        entry.setTeachingAssignmentId(rs.getLong("teaching_assignment_id"));
        entry.setDayOfWeek(DayOfWeek.fromString(rs.getString("day_of_week")));
        entry.setPeriodNumber(rs.getInt("period_number"));
        long roomId = rs.getLong("room_id");
        if (!rs.wasNull()) {
            entry.setRoomId(roomId);
        }
        String slotType = rs.getString("slot_type");
        entry.setSlotType(slotType != null ? slotType : "PERIOD");
        long breakId = rs.getLong("break_id");
        if (!rs.wasNull()) {
            entry.setBreakId(breakId);
        }
        return entry;
    }
}
