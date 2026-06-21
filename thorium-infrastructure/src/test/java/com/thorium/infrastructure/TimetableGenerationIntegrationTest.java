package com.thorium.infrastructure;

import com.thorium.application.dto.*;
import com.thorium.application.usecase.assignment.AssignmentManagementUseCase;
import com.thorium.application.usecase.classstream.ClassStreamManagementUseCase;
import com.thorium.application.usecase.subject.SubjectManagementUseCase;
import com.thorium.application.usecase.teacher.TeacherManagementUseCase;
import com.thorium.application.usecase.timetable.GenerateTimetableUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class TimetableGenerationIntegrationTest {

    @TempDir
    Path tempDir;

    private GenerateTimetableUseCase generateUseCase;
    private TeacherManagementUseCase teacherUseCase;
    private SubjectManagementUseCase subjectUseCase;
    private ClassStreamManagementUseCase classUseCase;
    private AssignmentManagementUseCase assignmentUseCase;

    @BeforeEach
    void setUp() {
        ApplicationBootstrap bootstrap = ApplicationBootstrap.create(tempDir.resolve("integration.db"));
        generateUseCase = bootstrap.generateTimetableUseCase();
        teacherUseCase = bootstrap.teacherManagementUseCase();
        subjectUseCase = bootstrap.subjectManagementUseCase();
        classUseCase = bootstrap.classStreamManagementUseCase();
        assignmentUseCase = bootstrap.assignmentManagementUseCase();
        seedData();
    }

    @Test
    void generatesTimetableEndToEnd() {
        TimetableDto timetable = generateUseCase.execute("Term 1 Timetable");

        assertNotNull(timetable.id());
        assertEquals("Term 1 Timetable", timetable.name());
        assertEquals(5, timetable.entries().size());
        assertTrue(timetable.qualityScore() > 0);
    }

    @Test
    void failsWhenNoAssignmentsExist() {
        ApplicationBootstrap emptyBootstrap = ApplicationBootstrap.create(tempDir.resolve("empty.db"));
        GenerateTimetableUseCase emptyUseCase = emptyBootstrap.generateTimetableUseCase();

        assertThrows(IllegalStateException.class, () -> emptyUseCase.execute("Empty"));
    }

    private void seedData() {
        TeacherDto teacher = teacherUseCase.create(new TeacherDto(null, "T001", "John Doe", 6, 30, true));
        SubjectDto subject = subjectUseCase.create(new SubjectDto(
                null, "S001", "Geography", true, 5, false, false));
        ClassStreamDto classStream = classUseCase.create(new ClassStreamDto(
                null, "F1E", 1, "East", "Form 1 East"));

        assignmentUseCase.create(new TeachingAssignmentDto(
                null,
                teacher.id(), teacher.name(),
                subject.id(), subject.name(),
                classStream.id(), classStream.displayName(),
                5));
    }
}
