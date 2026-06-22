package com.thorium.application.dto;

public record SchoolSettingsDto(
        Long id,
        int totalPeriods,
        String startTime,
        String endTime,
        int periodDurationMinutes
) {
}
