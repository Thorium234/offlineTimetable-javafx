package com.thorium.application.usecase.settings;

import com.thorium.application.port.SchoolSettingsRepository;
import com.thorium.domain.model.SchoolSettings;

import java.time.LocalTime;

class InMemorySchoolSettingsRepository implements SchoolSettingsRepository {

    private SchoolSettings settings;

    InMemorySchoolSettingsRepository() {
        this.settings = new SchoolSettings(1L, 8, LocalTime.of(8, 0), LocalTime.of(16, 0), 40);
    }

    @Override
    public SchoolSettings get() {
        return copy(settings);
    }

    @Override
    public SchoolSettings save(SchoolSettings settings) {
        this.settings = copy(settings);
        this.settings.setId(1L);
        return this.settings;
    }

    private SchoolSettings copy(SchoolSettings s) {
        SchoolSettings copy = new SchoolSettings(
                s.getId(), s.getTotalPeriods(), s.getStartTime(), s.getEndTime(),
                s.getPeriodDurationMinutes(), s.getSpreadWeight(), s.getConsecutiveWeight(),
                s.getBalanceWeight()
        );
        return copy;
    }
}
