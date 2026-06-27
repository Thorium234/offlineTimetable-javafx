package com.thorium.application.dto;

public record TeacherSubjectDto(
        Long id,
        Long teacherId,
        String teacherName,
        Long subjectId,
        String subjectName,
        String subjectCode
) {
}
