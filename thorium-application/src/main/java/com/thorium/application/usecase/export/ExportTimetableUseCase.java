package com.thorium.application.usecase.export;

import com.thorium.application.port.ClassStreamRepository;
import com.thorium.application.port.TeacherRepository;
import com.thorium.application.port.TimetableExporter;
import com.thorium.application.port.TimetableRepository;
import com.thorium.domain.model.ClassStream;
import com.thorium.domain.model.Teacher;
import com.thorium.domain.model.Timetable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public class ExportTimetableUseCase {

    private final TimetableRepository timetableRepository;
    private final TeacherRepository teacherRepository;
    private final ClassStreamRepository classStreamRepository;
    private final TimetableExporter teacherExporter;
    private final TimetableExporter classExporter;

    public ExportTimetableUseCase(TimetableRepository timetableRepository,
                                   TeacherRepository teacherRepository,
                                   ClassStreamRepository classStreamRepository,
                                   TimetableExporter teacherExporter,
                                   TimetableExporter classExporter) {
        this.timetableRepository = timetableRepository;
        this.teacherRepository = teacherRepository;
        this.classStreamRepository = classStreamRepository;
        this.teacherExporter = teacherExporter;
        this.classExporter = classExporter;
    }

    public byte[] previewTeacherPdf(Long timetableId, Long teacherId) {
        var data = timetableRepository.findByIdWithEntries(timetableId)
                .orElseThrow(() -> new IllegalArgumentException("Timetable not found: " + timetableId));
        return teacherExporter.renderTeacherPdfToBytes(data, teacherId);
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

    public List<ClassStream> findAllClasses() {
        return classStreamRepository.findAll();
    }

    public Optional<ClassStream> findClassStream(Long id) {
        return classStreamRepository.findById(id);
    }

    public byte[] previewClassPdf(Long timetableId, Long classStreamId) {
        var data = timetableRepository.findByIdWithEntries(timetableId)
                .orElseThrow(() -> new IllegalArgumentException("Timetable not found: " + timetableId));
        return classExporter.renderClassPdfToBytes(data, classStreamId);
    }

    public void exportClassPdf(Long timetableId, Path outputPath, Long classStreamId) {
        byte[] pdf = previewClassPdf(timetableId, classStreamId);
        try {
            Files.write(outputPath, pdf);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write PDF to " + outputPath, e);
        }
    }

    public byte[] previewAllClassesPdf(Long timetableId) {
        var data = timetableRepository.findByIdWithEntries(timetableId)
                .orElseThrow(() -> new IllegalArgumentException("Timetable not found: " + timetableId));
        return classExporter.renderAllClassesPdfToBytes(data);
    }

    public void exportAllClassesPdf(Long timetableId, Path outputPath) {
        byte[] pdf = previewAllClassesPdf(timetableId);
        try {
            Files.write(outputPath, pdf);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write PDF to " + outputPath, e);
        }
    }
}
