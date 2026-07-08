package com.thorium.application.usecase.export;

import com.thorium.application.port.TimetableExporter;
import com.thorium.application.port.TimetableRepository;

import java.io.IOException;
import java.nio.file.Path;

public class ExportTimetableUseCase {

    private final TimetableRepository timetableRepository;
    private final TimetableExporter exporter;

    public ExportTimetableUseCase(TimetableRepository timetableRepository, TimetableExporter exporter) {
        this.timetableRepository = timetableRepository;
        this.exporter = exporter;
    }

    public void exportPdf(Long timetableId, Path outputPath) {
        TimetableRepository.TimetableWithEntries data = timetableRepository.findByIdWithEntries(timetableId)
                .orElseThrow(() -> new IllegalArgumentException("Timetable not found: " + timetableId));
        exporter.exportPdf(data, outputPath);
    }

    public void exportExcel(Long timetableId, Path outputPath) {
        TimetableRepository.TimetableWithEntries data = timetableRepository.findByIdWithEntries(timetableId)
                .orElseThrow(() -> new IllegalArgumentException("Timetable not found: " + timetableId));
        exporter.exportExcel(data, outputPath);
    }

    public byte[] previewPdf(Long timetableId) {
        TimetableRepository.TimetableWithEntries data = timetableRepository.findByIdWithEntries(timetableId)
                .orElseThrow(() -> new IllegalArgumentException("Timetable not found: " + timetableId));
        return exporter.renderPdfToBytes(data);
    }

    public byte[] previewTeacherPdf(Long timetableId, Long teacherId) {
        TimetableRepository.TimetableWithEntries data = timetableRepository.findByIdWithEntries(timetableId)
                .orElseThrow(() -> new IllegalArgumentException("Timetable not found: " + timetableId));
        return exporter.renderTeacherPdfToBytes(data, teacherId);
    }

    public byte[] previewStreamPdf(Long timetableId, String stream) {
        TimetableRepository.TimetableWithEntries data = timetableRepository.findByIdWithEntries(timetableId)
                .orElseThrow(() -> new IllegalArgumentException("Timetable not found: " + timetableId));
        return exporter.renderStreamPdfToBytes(data, stream);
    }

    public byte[] previewGradePdf(Long timetableId, int form) {
        TimetableRepository.TimetableWithEntries data = timetableRepository.findByIdWithEntries(timetableId)
                .orElseThrow(() -> new IllegalArgumentException("Timetable not found: " + timetableId));
        return exporter.renderGradePdfToBytes(data, form);
    }

    public void exportTeacherPdf(Long timetableId, Path outputPath, Long teacherId) {
        TimetableRepository.TimetableWithEntries data = timetableRepository.findByIdWithEntries(timetableId)
                .orElseThrow(() -> new IllegalArgumentException("Timetable not found: " + timetableId));
        byte[] pdfBytes = exporter.renderTeacherPdfToBytes(data, teacherId);
        try {
            java.nio.file.Files.write(outputPath, pdfBytes);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to write PDF file", e);
        }
    }

    public void exportStreamPdf(Long timetableId, Path outputPath, String stream) {
        TimetableRepository.TimetableWithEntries data = timetableRepository.findByIdWithEntries(timetableId)
                .orElseThrow(() -> new IllegalArgumentException("Timetable not found: " + timetableId));
        byte[] pdfBytes = exporter.renderStreamPdfToBytes(data, stream);
        try {
            java.nio.file.Files.write(outputPath, pdfBytes);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to write PDF file", e);
        }
    }

    public void exportGradePdf(Long timetableId, Path outputPath, int form) {
        TimetableRepository.TimetableWithEntries data = timetableRepository.findByIdWithEntries(timetableId)
                .orElseThrow(() -> new IllegalArgumentException("Timetable not found: " + timetableId));
        byte[] pdfBytes = exporter.renderGradePdfToBytes(data, form);
        try {
            java.nio.file.Files.write(outputPath, pdfBytes);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to write PDF file", e);
        }
    }

    public byte[] previewAllTeachersPdf(Long timetableId) {
        TimetableRepository.TimetableWithEntries data = timetableRepository.findByIdWithEntries(timetableId)
                .orElseThrow(() -> new IllegalArgumentException("Timetable not found: " + timetableId));
        return exporter.renderAllTeachersPdfToBytes(data);
    }

    public void exportAllTeachersPdf(Long timetableId, Path outputPath) {
        TimetableRepository.TimetableWithEntries data = timetableRepository.findByIdWithEntries(timetableId)
                .orElseThrow(() -> new IllegalArgumentException("Timetable not found: " + timetableId));
        byte[] pdfBytes = exporter.renderAllTeachersPdfToBytes(data);
        try {
            java.nio.file.Files.write(outputPath, pdfBytes);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to write PDF file", e);
        }
    }

    public byte[] previewAllClassesPdf(Long timetableId) {
        TimetableRepository.TimetableWithEntries data = timetableRepository.findByIdWithEntries(timetableId)
                .orElseThrow(() -> new IllegalArgumentException("Timetable not found: " + timetableId));
        return exporter.renderAllClassesPdfToBytes(data);
    }

    public void exportAllClassesPdf(Long timetableId, Path outputPath) {
        TimetableRepository.TimetableWithEntries data = timetableRepository.findByIdWithEntries(timetableId)
                .orElseThrow(() -> new IllegalArgumentException("Timetable not found: " + timetableId));
        byte[] pdfBytes = exporter.renderAllClassesPdfToBytes(data);
        try {
            java.nio.file.Files.write(outputPath, pdfBytes);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to write PDF file", e);
        }
    }

    public byte[] previewClassPdf(Long timetableId, int form, String stream) {
        TimetableRepository.TimetableWithEntries data = timetableRepository.findByIdWithEntries(timetableId)
                .orElseThrow(() -> new IllegalArgumentException("Timetable not found: " + timetableId));
        return exporter.renderStreamPdfToBytes(data, stream);
    }

    public void exportClassPdf(Long timetableId, Path outputPath, int form, String stream) {
        TimetableRepository.TimetableWithEntries data = timetableRepository.findByIdWithEntries(timetableId)
                .orElseThrow(() -> new IllegalArgumentException("Timetable not found: " + timetableId));
        byte[] pdfBytes = exporter.renderStreamPdfToBytes(data, stream);
        try {
            java.nio.file.Files.write(outputPath, pdfBytes);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to write PDF file", e);
        }
    }

    // ---- aSc-style teacher timetable export ----

    public byte[] previewAscTeacherPdf(Long timetableId, Long teacherId) {
        TimetableRepository.TimetableWithEntries data = timetableRepository.findByIdWithEntries(timetableId)
                .orElseThrow(() -> new IllegalArgumentException("Timetable not found: " + timetableId));
        return exporter.renderAscTeacherPdfToBytes(data, teacherId);
    }

    public void exportAscTeacherPdf(Long timetableId, Path outputPath, Long teacherId) {
        TimetableRepository.TimetableWithEntries data = timetableRepository.findByIdWithEntries(timetableId)
                .orElseThrow(() -> new IllegalArgumentException("Timetable not found: " + timetableId));
        byte[] pdfBytes = exporter.renderAscTeacherPdfToBytes(data, teacherId);
        try {
            java.nio.file.Files.write(outputPath, pdfBytes);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to write PDF file", e);
        }
    }

    public byte[] previewAscAllTeachersPdf(Long timetableId) {
        TimetableRepository.TimetableWithEntries data = timetableRepository.findByIdWithEntries(timetableId)
                .orElseThrow(() -> new IllegalArgumentException("Timetable not found: " + timetableId));
        return exporter.renderAscAllTeachersPdfToBytes(data);
    }

    public void exportAscAllTeachersPdf(Long timetableId, Path outputPath) {
        TimetableRepository.TimetableWithEntries data = timetableRepository.findByIdWithEntries(timetableId)
                .orElseThrow(() -> new IllegalArgumentException("Timetable not found: " + timetableId));
        byte[] pdfBytes = exporter.renderAscAllTeachersPdfToBytes(data);
        try {
            java.nio.file.Files.write(outputPath, pdfBytes);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to write PDF file", e);
        }
    }
}
