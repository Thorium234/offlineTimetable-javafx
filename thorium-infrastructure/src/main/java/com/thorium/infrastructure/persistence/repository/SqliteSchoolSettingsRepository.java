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
            String sql = "INSERT OR REPLACE INTO school_settings (id, school_name, total_periods, school_start_time, school_end_time, period_duration_min, spread_weight, consecutive_weight, balance_weight) VALUES (1, ?, ?, ?, ?, ?, ?, ?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, settings.getSchoolName());
                ps.setInt(2, settings.getTotalPeriods());
                ps.setString(3, settings.getStartTime().format(TIME_FORMAT));
                ps.setString(4, settings.getEndTime().format(TIME_FORMAT));
                ps.setInt(5, settings.getPeriodDurationMinutes());
                ps.setDouble(6, settings.getSpreadWeight());
                ps.setDouble(7, settings.getConsecutiveWeight());
                ps.setDouble(8, settings.getBalanceWeight());
                ps.executeUpdate();
            }
            commit(conn);
            return settings;
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to save school settings", e);
        }
    }

    private SchoolSettings map(ResultSet rs) throws SQLException {
        LocalTime endTime = rs.getString("school_end_time") != null
                ? LocalTime.parse(rs.getString("school_end_time"), TIME_FORMAT)
                : LocalTime.of(16, 0);
        double spreadWeight = 0.50;
        double consecutiveWeight = 0.40;
        double balanceWeight = 0.10;
        try {
            spreadWeight = rs.getDouble("spread_weight");
            consecutiveWeight = rs.getDouble("consecutive_weight");
            balanceWeight = rs.getDouble("balance_weight");
        } catch (SQLException ignored) {
        }
        String schoolName = "My School";
        try {
            schoolName = rs.getString("school_name");
            if (schoolName == null || schoolName.isBlank()) schoolName = "My School";
        } catch (SQLException ignored) {
        }
        return new SchoolSettings(
                rs.getLong("id"),
                schoolName,
                rs.getInt("total_periods"),
                LocalTime.parse(rs.getString("school_start_time"), TIME_FORMAT),
                endTime,
                rs.getInt("period_duration_min"),
                spreadWeight,
                consecutiveWeight,
                balanceWeight
        );
    }

    private SchoolSettings createDefaults() {
        return new SchoolSettings(1L, "My School", 8, LocalTime.of(8, 0), LocalTime.of(16, 0), 40, 0.50, 0.40, 0.10);
    }
}
