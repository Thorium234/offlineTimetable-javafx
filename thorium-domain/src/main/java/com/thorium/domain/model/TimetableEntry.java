package com.thorium.domain.model;

import com.thorium.domain.value.DayOfWeek;

import java.util.Objects;

public class TimetableEntry {

    private Long id;
    private Long timetableId;
    private Long teachingAssignmentId;
    private DayOfWeek dayOfWeek;
    private int periodNumber;
    private Long roomId;
    private String slotType;
    private Long breakId;

    public TimetableEntry() {
        this.slotType = "PERIOD";
    }

    public TimetableEntry(Long id, Long timetableId, Long teachingAssignmentId, DayOfWeek dayOfWeek,
                          int periodNumber, Long roomId) {
        this.id = id;
        this.timetableId = timetableId;
        this.teachingAssignmentId = teachingAssignmentId;
        this.dayOfWeek = dayOfWeek;
        this.periodNumber = periodNumber;
        this.roomId = roomId;
        this.slotType = "PERIOD";
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTimetableId() { return timetableId; }
    public void setTimetableId(Long timetableId) { this.timetableId = timetableId; }
    public Long getTeachingAssignmentId() { return teachingAssignmentId; }
    public void setTeachingAssignmentId(Long teachingAssignmentId) { this.teachingAssignmentId = teachingAssignmentId; }
    public DayOfWeek getDayOfWeek() { return dayOfWeek; }
    public void setDayOfWeek(DayOfWeek dayOfWeek) { this.dayOfWeek = dayOfWeek; }
    public int getPeriodNumber() { return periodNumber; }
    public void setPeriodNumber(int periodNumber) { this.periodNumber = periodNumber; }
    public Long getRoomId() { return roomId; }
    public void setRoomId(Long roomId) { this.roomId = roomId; }
    public String getSlotType() { return slotType; }
    public void setSlotType(String slotType) { this.slotType = slotType; }
    public Long getBreakId() { return breakId; }
    public void setBreakId(Long breakId) { this.breakId = breakId; }

    public ScheduleSlot toSlot() {
        return new ScheduleSlot(dayOfWeek, periodNumber);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) { return true; }
        if (!(o instanceof TimetableEntry that)) { return false; }
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
