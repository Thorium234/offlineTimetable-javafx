package com.thorium.application.port;

import com.thorium.application.port.TimetableRepository.TimetableWithEntries;

import java.nio.file.Path;

public interface TimetableExporter {

    void exportPdf(TimetableWithEntries data, Path outputPath);

    void exportExcel(TimetableWithEntries data, Path outputPath);

    byte[] renderPdfToBytes(TimetableWithEntries data);

    byte[] renderTeacherPdfToBytes(TimetableWithEntries data, Long teacherId);

    byte[] renderStreamPdfToBytes(TimetableWithEntries data, String stream);

    byte[] renderGradePdfToBytes(TimetableWithEntries data, int form);

    byte[] renderAllTeachersPdfToBytes(TimetableWithEntries data);
}
