package com.thorium.domain.model.timeblock;

import java.time.Duration;
import java.time.LocalTime;

public record LessonBlock(int periodNumber, LocalTime startTime, LocalTime endTime) implements TimeBlock {

    public LessonBlock {
        if (startTime == null) throw new IllegalArgumentException("startTime must not be null");
        if (endTime == null) throw new IllegalArgumentException("endTime must not be null");
        if (!endTime.isAfter(startTime)) throw new IllegalArgumentException("endTime must be after startTime");
    }

    @Override
    public int durationMinutes() {
        return (int) Duration.between(startTime, endTime).toMinutes();
    }

    @Override
    public BlockType blockType() {
        return BlockType.LESSON;
    }

    @Override
    public String label() {
        return "Period " + periodNumber;
    }
}
