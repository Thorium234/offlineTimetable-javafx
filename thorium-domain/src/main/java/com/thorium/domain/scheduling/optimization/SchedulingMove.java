package com.thorium.domain.scheduling.optimization;

import com.thorium.domain.model.ScheduleSlot;

public record SchedulingMove(int lesson1Idx, int lesson2Idx, ScheduleSlot slot2, ScheduleSlot slot1) {
}
