package com.thorium.application.usecase.settings;

import com.thorium.application.dto.SchoolSettingsDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SchoolSettingsUseCaseTest {

    private SchoolSettingsUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new SchoolSettingsUseCase(new InMemorySchoolSettingsRepository());
    }

    @Test
    void returnsDefaultSettings() {
        SchoolSettingsDto dto = useCase.getSettings();
        assertEquals(8, dto.totalPeriods());
        assertEquals(0.50, dto.spreadWeight());
        assertEquals(0.40, dto.consecutiveWeight());
        assertEquals(0.10, dto.balanceWeight());
    }

    @Test
    void updatesSoftConstraintWeights() {
        SchoolSettingsDto updated = useCase.updateSettings(new SchoolSettingsDto(
                1L, 8, "08:00", "16:00", 40, 0.60, 0.30, 0.10));

        assertEquals(0.60, updated.spreadWeight());
        assertEquals(0.30, updated.consecutiveWeight());
        assertEquals(0.10, updated.balanceWeight());
    }

    @Test
    void rejectsInvalidPeriodCount() {
        assertThrows(IllegalArgumentException.class, () ->
                useCase.updateSettings(new SchoolSettingsDto(
                        1L, 0, "08:00", "16:00", 40, 0.5, 0.4, 0.1)));
    }
}
