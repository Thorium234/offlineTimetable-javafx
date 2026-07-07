package com.thorium.domain.model;

import java.time.LocalTime;

public class SchoolSettings {

    private Long id;
    private String schoolName;
    private int totalPeriods;
    private LocalTime startTime;
    private LocalTime endTime;
    private int periodDurationMinutes;
    private double spreadWeight;
    private double consecutiveWeight;
    private double balanceWeight;

    public SchoolSettings() {
        this.schoolName = "My School";
        this.spreadWeight = 0.50;
        this.consecutiveWeight = 0.40;
        this.balanceWeight = 0.10;
    }

    public SchoolSettings(Long id, String schoolName, int totalPeriods, LocalTime startTime, LocalTime endTime, int periodDurationMinutes) {
        this(id, schoolName, totalPeriods, startTime, endTime, periodDurationMinutes, 0.50, 0.40, 0.10);
    }

    public SchoolSettings(Long id, String schoolName, int totalPeriods, LocalTime startTime, LocalTime endTime,
                          int periodDurationMinutes, double spreadWeight, double consecutiveWeight,
                          double balanceWeight) {
        this.id = id;
        this.schoolName = schoolName;
        this.totalPeriods = totalPeriods;
        this.startTime = startTime;
        this.endTime = endTime;
        this.periodDurationMinutes = periodDurationMinutes;
        this.spreadWeight = spreadWeight;
        this.consecutiveWeight = consecutiveWeight;
        this.balanceWeight = balanceWeight;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getSchoolName() { return schoolName; }
    public void setSchoolName(String schoolName) { this.schoolName = schoolName; }
    public int getTotalPeriods() { return totalPeriods; }
    public void setTotalPeriods(int totalPeriods) { this.totalPeriods = totalPeriods; }
    public LocalTime getStartTime() { return startTime; }
    public void setStartTime(LocalTime startTime) { this.startTime = startTime; }
    public LocalTime getEndTime() { return endTime; }
    public void setEndTime(LocalTime endTime) { this.endTime = endTime; }
    public int getPeriodDurationMinutes() { return periodDurationMinutes; }
    public void setPeriodDurationMinutes(int periodDurationMinutes) { this.periodDurationMinutes = periodDurationMinutes; }
    public double getSpreadWeight() { return spreadWeight; }
    public void setSpreadWeight(double spreadWeight) { this.spreadWeight = spreadWeight; }
    public double getConsecutiveWeight() { return consecutiveWeight; }
    public void setConsecutiveWeight(double consecutiveWeight) { this.consecutiveWeight = consecutiveWeight; }
    public double getBalanceWeight() { return balanceWeight; }
    public void setBalanceWeight(double balanceWeight) { this.balanceWeight = balanceWeight; }
}
