package com.thorium.application.port;

import com.thorium.application.port.TimetableRepository.TimetableWithEntries;

public interface TimetableExporter {

    byte[] renderTeacherPdfToBytes(TimetableWithEntries data, Long teacherId);

    byte[] renderClassPdfToBytes(TimetableWithEntries data, Long classStreamId);

    byte[] renderAllClassesPdfToBytes(TimetableWithEntries data);
}
