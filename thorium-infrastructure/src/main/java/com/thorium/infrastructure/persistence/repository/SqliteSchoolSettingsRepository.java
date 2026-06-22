package com.thorium.infrastructure.persistence.repository;

import com.thorium.application.port.SchoolSettingsRepository;
import com.thorium.domain.model.SchoolSettings;
import com.thorium.infrastructure.persistence.SQLiteConnectionProvider;

import java.sql.*;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public class SqliteSchoolSettingsRepository extends AbstractRepository implements SchoolSettingsRepository {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    public SqliteSchoolSettingsRepository(SQLiteConnectionProvider connectionProvider) {
        super(connectionProvider);
    }

    @Override
    public SchoolSettings get() {
        try (Connection conn = connection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM school_settings WHERE id = 1");
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return map(rs);
            }
            SchoolSettings defaults = createDefaults();
            save(defaults);
            return defaults;
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to get school settings", e);
        }
    }

    @Override
    public SchoolSettings save(SchoolSettings settings) {
        try (Connection conn = connection()) {
            String sql = "INSERT OR REPLACE INTO school_settings (id, total_periods, school_start_time, period_duration_min) VALUES (1, ?, ?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, settings.getTotalPeriods());
                ps.setString(2, settings.getStartTime().format(TIME_FORMAT));
                ps.setInt(3, settings.getPeriodDurationMinutes());
                ps.executeUpdate();
            }
            commit(conn);
            return settings;
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to save school settings", e);
        }
    }

    private SchoolSettings map(ResultSet rs) throws SQLException {
        return new SchoolSettings(
                rs.getLong("id"),
                rs.getInt("total_periods"),
                LocalTime.parse(rs.getString("school_start_time"), TIME_FORMAT),
                rs.getInt("period_duration_min")
        );
    }

    private SchoolSettings createDefaults() {
        return new SchoolSettings(1L, 8, LocalTime.of(8, 0), 40);
    }
}
