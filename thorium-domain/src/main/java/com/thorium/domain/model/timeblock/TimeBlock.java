package com.thorium.domain.model.timeblock;

import java.time.LocalTime;

public interface TimeBlock {
    LocalTime startTime();
    LocalTime endTime();
    int durationMinutes();
    BlockType blockType();
    String label();
}
