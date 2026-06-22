package com.thorium.application.dto;

public record BreakDto(
        Long id,
        String name,
        int afterPeriod,
        int durationMinutes,
        int sortOrder,
        boolean isBeforePeriodOne,
        boolean slotable,
        String startTime,
        String endTime
) {
}
