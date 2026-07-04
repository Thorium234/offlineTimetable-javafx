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
    private TeacherDto teacher;
    private SubjectDto subject;
    private ClassStreamDto classStream;

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

    @Test
    void exportsTeacherPdfSuccessfully() throws Exception {
        TimetableDto timetable = generateUseCase.execute("Test Timetable");
        Path pdfPath = tempDir.resolve("teacher.pdf");

        bootstrap.exportTimetableUseCase().exportTeacherPdf(timetable.id(), pdfPath, teacher.id());

        assertTrue(Files.exists(pdfPath));
        assertTrue(Files.size(pdfPath) > 0);
    }

    @Test
    void exportsStreamPdfSuccessfully() throws Exception {
        TimetableDto timetable = generateUseCase.execute("Test Timetable");
        Path pdfPath = tempDir.resolve("stream.pdf");

        bootstrap.exportTimetableUseCase().exportStreamPdf(timetable.id(), pdfPath, classStream.stream());

        assertTrue(Files.exists(pdfPath));
        assertTrue(Files.size(pdfPath) > 0);
    }

    @Test
    void exportsGradePdfSuccessfully() throws Exception {
        TimetableDto timetable = generateUseCase.execute("Test Timetable");
        Path pdfPath = tempDir.resolve("grade.pdf");

        bootstrap.exportTimetableUseCase().exportGradePdf(timetable.id(), pdfPath, classStream.form());

        assertTrue(Files.exists(pdfPath));
        assertTrue(Files.size(pdfPath) > 0);
    }

    private void seedData() {
        teacher = bootstrap.teacherManagementUseCase()
                .create(new TeacherDto(null, "T001", "John Doe", true));
        subject = bootstrap.subjectManagementUseCase()
                .create(new SubjectDto(null, "S001", "Geography", true, 5, false, false));
        classStream = bootstrap.classStreamManagementUseCase()
                .create(new ClassStreamDto(null, "F1E", 1, "East", "Form 1 East"));

        bootstrap.assignmentManagementUseCase()
                .create(new TeachingAssignmentDto(
                        null, teacher.id(), teacher.name(),
                        subject.id(), subject.name(),
                        classStream.id(), classStream.displayName(), 5,
                        com.thorium.domain.model.LessonDuration.SINGLE));
    }
}
