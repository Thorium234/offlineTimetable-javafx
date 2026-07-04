package com.thorium.domain.scheduling;

import com.thorium.domain.model.ScheduleSlot;
import com.thorium.domain.model.TeachingAssignment;

public record PlacedLesson(
        TeachingAssignment assignment,
        ScheduleSlot slot,
        Long entryId,
        Long roomId
) {
    public PlacedLesson(TeachingAssignment assignment, ScheduleSlot slot) {
        this(assignment, slot, null, null);
    }

    public PlacedLesson(TeachingAssignment assignment, ScheduleSlot slot, Long entryId) {
        this(assignment, slot, entryId, null);
    }

    public PlacedLesson withRoomId(Long roomId) {
        return new PlacedLesson(assignment, slot, entryId, roomId);
    }
}
