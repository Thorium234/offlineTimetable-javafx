package com.thorium.domain.model;

import java.time.LocalTime;

public class SchoolSettings {

    private Long id;
    private int totalPeriods;
    private LocalTime startTime;
    private int periodDurationMinutes;

    public SchoolSettings() {
    }

    public SchoolSettings(Long id, int totalPeriods, LocalTime startTime, int periodDurationMinutes) {
        this.id = id;
        this.totalPeriods = totalPeriods;
        this.startTime = startTime;
        this.periodDurationMinutes = periodDurationMinutes;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public int getTotalPeriods() { return totalPeriods; }
    public void setTotalPeriods(int totalPeriods) { this.totalPeriods = totalPeriods; }
    public LocalTime getStartTime() { return startTime; }
    public void setStartTime(LocalTime startTime) { this.startTime = startTime; }
    public int getPeriodDurationMinutes() { return periodDurationMinutes; }
    public void setPeriodDurationMinutes(int periodDurationMinutes) { this.periodDurationMinutes = periodDurationMinutes; }
}
