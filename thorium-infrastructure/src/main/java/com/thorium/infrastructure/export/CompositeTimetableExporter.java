package com.thorium.infrastructure.export;

import com.thorium.application.port.TimetableExporter;
import com.thorium.application.port.TimetableRepository;
import com.thorium.infrastructure.export.ExcelTimetableExporter;
import com.thorium.infrastructure.export.PdfTimetableExporter;

import java.nio.file.Path;

public class CompositeTimetableExporter implements TimetableExporter {

    private final PdfTimetableExporter pdfExporter;
    private final ExcelTimetableExporter excelExporter;
    private final AscStyleTeacherPdfExporter ascExporter;

    public CompositeTimetableExporter(PdfTimetableExporter pdfExporter, ExcelTimetableExporter excelExporter,
                                      AscStyleTeacherPdfExporter ascExporter) {
        this.pdfExporter = pdfExporter;
        this.excelExporter = excelExporter;
        this.ascExporter = ascExporter;
    }

    @Override
    public void exportPdf(TimetableRepository.TimetableWithEntries data, Path outputPath) {
        pdfExporter.exportPdf(data, outputPath);
    }

    @Override
    public void exportExcel(TimetableRepository.TimetableWithEntries data, Path outputPath) {
        excelExporter.exportExcel(data, outputPath);
    }

    @Override
    public byte[] renderPdfToBytes(TimetableRepository.TimetableWithEntries data) {
        return pdfExporter.renderPdfToBytes(data);
    }

    @Override
    public byte[] renderTeacherPdfToBytes(TimetableRepository.TimetableWithEntries data, Long teacherId) {
        return pdfExporter.renderTeacherPdfToBytes(data, teacherId);
    }

    @Override
    public byte[] renderStreamPdfToBytes(TimetableRepository.TimetableWithEntries data, String stream) {
        return pdfExporter.renderStreamPdfToBytes(data, stream);
    }

    @Override
    public byte[] renderGradePdfToBytes(TimetableRepository.TimetableWithEntries data, int form) {
        return pdfExporter.renderGradePdfToBytes(data, form);
    }

    @Override
    public byte[] renderAllTeachersPdfToBytes(TimetableRepository.TimetableWithEntries data) {
        return pdfExporter.renderAllTeachersPdfToBytes(data);
    }

    @Override
    public byte[] renderAllClassesPdfToBytes(TimetableRepository.TimetableWithEntries data) {
        return pdfExporter.renderAllClassesPdfToBytes(data);
    }

    @Override
    public byte[] renderAscTeacherPdfToBytes(TimetableRepository.TimetableWithEntries data, Long teacherId) {
        return ascExporter.renderAscTeacherPdfToBytes(data, teacherId);
    }

    @Override
    public byte[] renderAscAllTeachersPdfToBytes(TimetableRepository.TimetableWithEntries data) {
        return ascExporter.renderAscAllTeachersPdfToBytes(data);
    }
}
