package com.thorium.domain.model;

import java.time.LocalTime;
import java.util.Objects;

public class BreakPeriod {

    private Long id;
    private String name;
    private int afterPeriod;
    private int durationMinutes;
    private int sortOrder;
    private boolean isBeforePeriodOne;
    private boolean slotable;
    private LocalTime startTime;
    private LocalTime endTime;

    public BreakPeriod() {
    }

    public BreakPeriod(Long id, String name, int afterPeriod, int durationMinutes, int sortOrder, boolean isBeforePeriodOne, boolean slotable, LocalTime startTime, LocalTime endTime) {
        this.id = id;
        this.name = name;
        this.afterPeriod = afterPeriod;
        this.durationMinutes = durationMinutes;
        this.sortOrder = sortOrder;
        this.isBeforePeriodOne = isBeforePeriodOne;
        this.slotable = slotable;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public int getAfterPeriod() { return afterPeriod; }
    public void setAfterPeriod(int afterPeriod) { this.afterPeriod = afterPeriod; }
    public int getDurationMinutes() { return durationMinutes; }
    public void setDurationMinutes(int durationMinutes) { this.durationMinutes = durationMinutes; }
    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
    public boolean isBeforePeriodOne() { return isBeforePeriodOne; }
    public void setBeforePeriodOne(boolean beforePeriodOne) { isBeforePeriodOne = beforePeriodOne; }
    public boolean isSlotable() { return slotable; }
    public void setSlotable(boolean slotable) { this.slotable = slotable; }
    public LocalTime getStartTime() { return startTime; }
    public void setStartTime(LocalTime startTime) { this.startTime = startTime; }
    public LocalTime getEndTime() { return endTime; }
    public void setEndTime(LocalTime endTime) { this.endTime = endTime; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BreakPeriod that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
