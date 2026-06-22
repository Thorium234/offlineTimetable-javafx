package com.thorium.domain.model.timeblock;

import java.time.Duration;
import java.time.LocalTime;

public record BreakBlock(String name, LocalTime startTime, LocalTime endTime, int comesAfterPeriod)
        implements TimeBlock {

    public BreakBlock {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name must not be blank");
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
        return BlockType.BREAK;
    }

    @Override
    public String label() {
        return name;
    }
}
