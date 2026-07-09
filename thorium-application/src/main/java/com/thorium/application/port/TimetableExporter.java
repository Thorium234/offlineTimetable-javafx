package com.thorium.application.port;

import com.thorium.application.port.TimetableRepository.TimetableWithEntries;

public interface TimetableExporter {

    byte[] renderTeacherPdfToBytes(TimetableWithEntries data, Long teacherId);
}
