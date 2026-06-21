package com.thorium.infrastructure.export;

import com.thorium.application.dto.*;
import com.thorium.application.port.*;
import com.thorium.application.usecase.assignment.AssignmentManagementUseCase;
import com.thorium.application.usecase.classstream.ClassStreamManagementUseCase;
import com.thorium.application.usecase.subject.SubjectManagementUseCase;
import com.thorium.application.usecase.teacher.TeacherManagementUseCase;
import com.thorium.application.usecase.timetable.GenerateTimetableUseCase;
import com.thorium.infrastructure.ApplicationBootstrap;
import com.thorium.infrastructure.persistence.TestDatabaseSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ExportTimetableExporterTest {

    @TempDir
    Path tempDir;

    private ApplicationBootstrap bootstrap;
    private GenerateTimetableUseCase generateUseCase;

    @BeforeEach
    void setUp() throws Exception {
        bootstrap = TestDatabaseSupport.createBootstrap();
        generateUseCase = bootstrap.generateTimetableUseCase();
        seedData();
    }

    @Test
    void exportsPdfSuccessfully() throws Exception {
        TimetableDto timetable = generateUseCase.execute("Test Timetable");
        Path pdfPath = tempDir.resolve("output.pdf");

        bootstrap.exportTimetableUseCase().exportPdf(timetable.id(), pdfPath);

        assertTrue(Files.exists(pdfPath));
        assertTrue(Files.size(pdfPath) > 0);
    }

    @Test
    void exportsExcelSuccessfully() throws Exception {
        TimetableDto timetable = generateUseCase.execute("Test Timetable");
        Path excelPath = tempDir.resolve("output.xlsx");

        bootstrap.exportTimetableUseCase().exportExcel(timetable.id(), excelPath);

        assertTrue(Files.exists(excelPath));
        assertTrue(Files.size(excelPath) > 0);
    }

    @Test
    void pdfExportIsValidPdf() throws Exception {
        TimetableDto timetable = generateUseCase.execute("Test Timetable");
        Path pdfPath = tempDir.resolve("with-data.pdf");

        bootstrap.exportTimetableUseCase().exportPdf(timetable.id(), pdfPath);

        assertTrue(Files.exists(pdfPath));
        assertTrue(Files.size(pdfPath) > 0);
        byte[] header = new byte[5];
        try (var is = Files.newInputStream(pdfPath)) {
            is.read(header);
        }
        assertEquals("%PDF-", new String(header, java.nio.charset.StandardCharsets.US_ASCII));
    }

    private void seedData() {
        TeacherDto teacher = bootstrap.teacherManagementUseCase()
                .create(new TeacherDto(null, "T001", "John Doe", 6, 30, true));
        SubjectDto subject = bootstrap.subjectManagementUseCase()
                .create(new SubjectDto(null, "S001", "Geography", true, 5, false, false));
        ClassStreamDto classStream = bootstrap.classStreamManagementUseCase()
                .create(new ClassStreamDto(null, "F1E", 1, "East", "Form 1 East"));

        bootstrap.assignmentManagementUseCase()
                .create(new TeachingAssignmentDto(
                        null, teacher.id(), teacher.name(),
                        subject.id(), subject.name(),
                        classStream.id(), classStream.displayName(), 5));
    }
}
