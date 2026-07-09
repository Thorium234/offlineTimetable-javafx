package com.thorium.application.usecase.export;

import com.thorium.application.port.TeacherRepository;
import com.thorium.application.port.TimetableExporter;
import com.thorium.application.port.TimetableRepository;
import com.thorium.domain.model.Teacher;
import com.thorium.domain.model.Timetable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Optional;

public class ExportTimetableUseCase {

    private final TimetableRepository timetableRepository;
    private final TeacherRepository teacherRepository;
    private final TimetableExporter timetableExporter;

    public ExportTimetableUseCase(TimetableRepository timetableRepository,
                                   TeacherRepository teacherRepository,
                                   TimetableExporter timetableExporter) {
        this.timetableRepository = timetableRepository;
        this.teacherRepository = teacherRepository;
        this.timetableExporter = timetableExporter;
    }

    public byte[] previewTeacherPdf(Long timetableId, Long teacherId) {
        var data = timetableRepository.findByIdWithEntries(timetableId)
                .orElseThrow(() -> new IllegalArgumentException("Timetable not found: " + timetableId));
        return timetableExporter.renderTeacherPdfToBytes(data, teacherId);
    }

    public void exportTeacherPdf(Long timetableId, Path outputPath, Long teacherId) {
        byte[] pdf = previewTeacherPdf(timetableId, teacherId);
        try {
            Files.write(outputPath, pdf);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write PDF to " + outputPath, e);
        }
    }

    public Optional<Long> findLatestTimetableId() {
        return timetableRepository.findAll().stream()
                .max(Comparator.comparing(Timetable::getCreatedAt))
                .map(Timetable::getId);
    }

    public Optional<Teacher> findTeacher(Long teacherId) {
        return teacherRepository.findById(teacherId);
    }

    public java.util.List<Teacher> findAllTeachers() {
        return teacherRepository.findAll();
    }
}
