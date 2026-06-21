package com.thorium.infrastructure.persistence.repository;

import com.thorium.application.port.RoomRepository;
import com.thorium.domain.model.Room;
import com.thorium.domain.model.RoomType;
import com.thorium.infrastructure.persistence.SQLiteConnectionProvider;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class SqliteRoomRepository extends AbstractRepository implements RoomRepository {

    public SqliteRoomRepository(SQLiteConnectionProvider connectionProvider) {
        super(connectionProvider);
    }

    @Override
    public Room save(Room room) {
        try (Connection conn = connection()) {
            if (room.getId() == null) {
                insert(conn, room);
            } else {
                update(conn, room);
            }
            commit(conn);
            return room;
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to save room", e);
        }
    }

    private void insert(Connection conn, Room room) throws SQLException {
        String sql = """
                INSERT INTO rooms (code, name, type, capacity)
                VALUES (?, ?, ?, ?)
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bind(ps, room);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    room.setId(keys.getLong(1));
                }
            }
        }
    }

    private void update(Connection conn, Room room) throws SQLException {
        String sql = """
                UPDATE rooms SET code=?, name=?, type=?, capacity=?
                WHERE id=?
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            bind(ps, room);
            ps.setLong(5, room.getId());
            ps.executeUpdate();
        }
    }

    private void bind(PreparedStatement ps, Room room) throws SQLException {
        ps.setString(1, room.getCode());
        ps.setString(2, room.getName());
        ps.setString(3, room.getType().name());
        ps.setInt(4, room.getCapacity());
    }

    @Override
    public Optional<Room> findById(Long id) {
        try (Connection conn = connection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM rooms WHERE id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to find room", e);
        }
    }

    @Override
    public List<Room> findAll() {
        try (Connection conn = connection();
             ResultSet rs = conn.createStatement().executeQuery("SELECT * FROM rooms ORDER BY name")) {
            List<Room> list = new ArrayList<>();
            while (rs.next()) {
                list.add(map(rs));
            }
            return list;
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to list rooms", e);
        }
    }

    @Override
    public void deleteById(Long id) {
        try (Connection conn = connection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM rooms WHERE id = ?")) {
            ps.setLong(1, id);
            ps.executeUpdate();
            commit(conn);
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to delete room", e);
        }
    }

    @Override
    public long count() {
        try (Connection conn = connection();
             ResultSet rs = conn.createStatement().executeQuery("SELECT COUNT(*) FROM rooms")) {
            return rs.next() ? rs.getLong(1) : 0;
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to count rooms", e);
        }
    }

    private Room map(ResultSet rs) throws SQLException {
        Room room = new Room();
        room.setId(rs.getLong("id"));
        room.setCode(rs.getString("code"));
        room.setName(rs.getString("name"));
        room.setType(RoomType.valueOf(rs.getString("type")));
        room.setCapacity(rs.getInt("capacity"));
        return room;
    }
}
