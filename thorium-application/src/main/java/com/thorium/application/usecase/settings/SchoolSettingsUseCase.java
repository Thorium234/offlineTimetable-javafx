package com.thorium.application.usecase.settings;

import com.thorium.application.dto.SchoolSettingsDto;
import com.thorium.application.port.SchoolSettingsRepository;
import com.thorium.domain.model.SchoolSettings;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public class SchoolSettingsUseCase {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    private final SchoolSettingsRepository repository;

    public SchoolSettingsUseCase(SchoolSettingsRepository repository) {
        this.repository = repository;
    }

    public SchoolSettingsDto getSettings() {
        return toDto(repository.get());
    }

    public SchoolSettingsDto updateSettings(SchoolSettingsDto dto) {
        SchoolSettings settings = new SchoolSettings();
        settings.setId(1L);
        settings.setTotalPeriods(dto.totalPeriods());
        settings.setStartTime(LocalTime.parse(dto.startTime(), TIME_FORMAT));
        settings.setPeriodDurationMinutes(dto.periodDurationMinutes());
        validate(settings);
        return toDto(repository.save(settings));
    }

    private void validate(SchoolSettings settings) {
        if (settings.getTotalPeriods() < 1 || settings.getTotalPeriods() > 12) {
            throw new IllegalArgumentException("Total periods must be between 1 and 12");
        }
        if (settings.getPeriodDurationMinutes() < 20 || settings.getPeriodDurationMinutes() > 60) {
            throw new IllegalArgumentException("Period duration must be between 20 and 60 minutes");
        }
    }

    private static SchoolSettingsDto toDto(SchoolSettings s) {
        return new SchoolSettingsDto(
                s.getId() != null ? s.getId() : 1L,
                s.getTotalPeriods(),
                s.getStartTime().format(TIME_FORMAT),
                s.getPeriodDurationMinutes()
        );
    }
}
