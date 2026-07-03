package com.thorium.domain.model;

import java.util.Objects;

public class Teacher {

    private Long id;
    private String code;
    private String name;
    private boolean active;
    private int maxLessonsPerDay;
    private int maxLessonsPerWeek;

    public Teacher() {
        this.active = true;
        this.maxLessonsPerDay = 8;
        this.maxLessonsPerWeek = 40;
    }

    public Teacher(Long id, String code, String name, boolean active) {
        this(id, code, name, active, 8, 40);
    }

    public Teacher(Long id, String code, String name, boolean active, int maxLessonsPerDay, int maxLessonsPerWeek) {
        this.id = id;
        this.code = code;
        this.name = name;
        this.active = active;
        this.maxLessonsPerDay = maxLessonsPerDay;
        this.maxLessonsPerWeek = maxLessonsPerWeek;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public int getMaxLessonsPerDay() {
        return maxLessonsPerDay;
    }

    public void setMaxLessonsPerDay(int maxLessonsPerDay) {
        this.maxLessonsPerDay = maxLessonsPerDay;
    }

    public int getMaxLessonsPerWeek() {
        return maxLessonsPerWeek;
    }

    public void setMaxLessonsPerWeek(int maxLessonsPerWeek) {
        this.maxLessonsPerWeek = maxLessonsPerWeek;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Teacher teacher)) {
            return false;
        }
        return Objects.equals(id, teacher.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
