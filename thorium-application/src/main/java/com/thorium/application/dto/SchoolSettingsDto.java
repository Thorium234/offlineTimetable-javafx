package com.thorium.application.dto;

public record SchoolSettingsDto(
        Long id,
        String schoolName,
        int totalPeriods,
        String startTime,
        String endTime,
        int periodDurationMinutes,
        double spreadWeight,
        double consecutiveWeight,
        double balanceWeight
) {
}
