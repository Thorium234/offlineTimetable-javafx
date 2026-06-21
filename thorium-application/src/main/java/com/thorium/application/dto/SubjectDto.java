package com.thorium.application.dto;

public record SubjectDto(
        Long id,
        String code,
        String name,
        boolean examinable,
        int cbcDefaultLessons,
        boolean allowsDoublePeriod,
        boolean requiresDoublePeriod
) {
}
