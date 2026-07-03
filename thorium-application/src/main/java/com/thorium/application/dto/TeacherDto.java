package com.thorium.application.dto;

public record TeacherDto(
        Long id,
        String code,
        String name,
        boolean active,
        int maxLessonsPerDay,
        int maxLessonsPerWeek
) {
    public TeacherDto(Long id, String code, String name, boolean active) {
        this(id, code, name, active, 8, 40);
    }
}
