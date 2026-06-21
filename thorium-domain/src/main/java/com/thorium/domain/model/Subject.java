package com.thorium.domain.model;

import java.util.Objects;

public class Subject {

    private Long id;
    private String code;
    private String name;
    private boolean examinable;
    private int cbcDefaultLessons;
    private boolean allowsDoublePeriod;
    private boolean requiresDoublePeriod;

    public Subject() {
        this.cbcDefaultLessons = 5;
    }

    public Subject(Long id, String code, String name, boolean examinable, int cbcDefaultLessons, boolean allowsDoublePeriod, boolean requiresDoublePeriod) {
        this.id = id;
        this.code = code;
        this.name = name;
        this.examinable = examinable;
        this.cbcDefaultLessons = cbcDefaultLessons;
        this.allowsDoublePeriod = allowsDoublePeriod;
        this.requiresDoublePeriod = requiresDoublePeriod;
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

    public boolean isExaminable() {
        return examinable;
    }

    public void setExaminable(boolean examinable) {
        this.examinable = examinable;
    }

    public int getCbcDefaultLessons() {
        return cbcDefaultLessons;
    }

    public void setCbcDefaultLessons(int cbcDefaultLessons) {
        this.cbcDefaultLessons = cbcDefaultLessons;
    }

    public boolean isAllowsDoublePeriod() {
        return allowsDoublePeriod;
    }

    public void setAllowsDoublePeriod(boolean allowsDoublePeriod) {
        this.allowsDoublePeriod = allowsDoublePeriod;
    }

    public boolean isRequiresDoublePeriod() {
        return requiresDoublePeriod;
    }

    public void setRequiresDoublePeriod(boolean requiresDoublePeriod) {
        this.requiresDoublePeriod = requiresDoublePeriod;
    }

    public boolean isCbcSubject() {
        return examinable;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Subject subject)) {
            return false;
        }
        return Objects.equals(id, subject.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
