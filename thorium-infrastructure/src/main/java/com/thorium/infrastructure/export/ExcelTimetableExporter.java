package com.thorium.infrastructure.export;

import com.thorium.application.port.*;
import com.thorium.domain.model.TimetableEntry;
import com.thorium.domain.value.DayOfWeek;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class ExcelTimetableExporter implements TimetableExporter {

    private final TeachingAssignmentRepository assignmentRepository;
    private final SubjectRepository subjectRepository;
    private final TeacherRepository teacherRepository;
    private final ClassStreamRepository classStreamRepository;

    public ExcelTimetableExporter(TeachingAssignmentRepository assignmentRepository,
                                  SubjectRepository subjectRepository,
                                  TeacherRepository teacherRepository,
                                  ClassStreamRepository classStreamRepository) {
        this.assignmentRepository = assignmentRepository;
        this.subjectRepository = subjectRepository;
        this.teacherRepository = teacherRepository;
        this.classStreamRepository = classStreamRepository;
    }

    @Override
    public void exportPdf(TimetableRepository.TimetableWithEntries data, Path outputPath) {
        throw new UnsupportedOperationException("Use PdfTimetableExporter");
    }

    @Override
    public byte[] renderPdfToBytes(TimetableRepository.TimetableWithEntries data) {
        throw new UnsupportedOperationException("Use PdfTimetableExporter");
    }

    @Override
    public byte[] renderTeacherPdfToBytes(TimetableRepository.TimetableWithEntries data, Long teacherId) {
        throw new UnsupportedOperationException("Use PdfTimetableExporter");
    }

    @Override
    public byte[] renderStreamPdfToBytes(TimetableRepository.TimetableWithEntries data, String stream) {
        throw new UnsupportedOperationException("Use PdfTimetableExporter");
    }

    @Override
    public byte[] renderGradePdfToBytes(TimetableRepository.TimetableWithEntries data, int form) {
        throw new UnsupportedOperationException("Use PdfTimetableExporter");
    }

    @Override
    public byte[] renderAllTeachersPdfToBytes(TimetableRepository.TimetableWithEntries data) {
        throw new UnsupportedOperationException("Use PdfTimetableExporter");
    }

    @Override
    public byte[] renderAllClassesPdfToBytes(TimetableRepository.TimetableWithEntries data) {
        throw new UnsupportedOperationException("Use PdfTimetableExporter");
    }

    @Override
    public void exportExcel(TimetableRepository.TimetableWithEntries data, Path outputPath) {
        try (Workbook workbook = new XSSFWorkbook()) {
            Map<Long, List<TimetableEntry>> byClass = new TreeMap<>();
            for (TimetableEntry entry : data.entries()) {
                var assignment = assignmentRepository.findById(entry.getTeachingAssignmentId()).orElseThrow();
                byClass.computeIfAbsent(assignment.getClassStreamId(), k -> new ArrayList<>()).add(entry);
            }

            for (Map.Entry<Long, List<TimetableEntry>> classEntries : byClass.entrySet()) {
                String sheetName = classStreamRepository.findById(classEntries.getKey())
                        .map(c -> c.getCode())
                        .orElse("Class" + classEntries.getKey());
                Sheet sheet = workbook.createSheet(sheetName.substring(0, Math.min(31, sheetName.length())));

                Row header = sheet.createRow(0);
                header.createCell(0).setCellValue("Period");
                int col = 1;
                for (DayOfWeek day : DayOfWeek.workingDays()) {
                    header.createCell(col++).setCellValue(day.displayName());
                }

                int maxPeriod = classEntries.getValue().stream()
                        .mapToInt(TimetableEntry::getPeriodNumber).max().orElse(8);

                for (int period = 1; period <= maxPeriod; period++) {
                    Row row = sheet.createRow(period);
                    row.createCell(0).setCellValue("P" + period);
                    col = 1;
                    for (DayOfWeek day : DayOfWeek.workingDays()) {
                        final int p = period;
                        Optional<TimetableEntry> match = classEntries.getValue().stream()
                                .filter(e -> e.getDayOfWeek() == day && e.getPeriodNumber() == p)
                                .findFirst();
                        row.createCell(col++).setCellValue(match.map(this::formatEntry).orElse(""));
                    }
                }

                for (int i = 0; i <= DayOfWeek.workingDays().size(); i++) {
                    sheet.autoSizeColumn(i);
                }
            }

            try (OutputStream out = Files.newOutputStream(outputPath)) {
                workbook.write(out);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to export Excel", e);
        }
    }

    private String formatEntry(TimetableEntry entry) {
        var assignment = assignmentRepository.findById(entry.getTeachingAssignmentId()).orElse(null);
        if (assignment == null) {
            return "";
        }
        String subject = subjectRepository.findById(assignment.getSubjectId()).map(s -> s.getName()).orElse("?");
        String teacher = teacherRepository.findById(assignment.getTeacherId()).map(t -> t.getName()).orElse("?");
        return subject + " (" + teacher + ")";
    }
}
