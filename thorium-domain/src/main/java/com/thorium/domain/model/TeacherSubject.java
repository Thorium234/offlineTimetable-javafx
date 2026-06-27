package com.thorium.domain.model;

import java.util.Objects;

public class TeacherSubject {

    private Long id;
    private Long teacherId;
    private Long subjectId;

    public TeacherSubject() {
    }

    public TeacherSubject(Long id, Long teacherId, Long subjectId) {
        this.id = id;
        this.teacherId = teacherId;
        this.subjectId = subjectId;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getTeacherId() {
        return teacherId;
    }

    public void setTeacherId(Long teacherId) {
        this.teacherId = teacherId;
    }

    public Long getSubjectId() {
        return subjectId;
    }

    public void setSubjectId(Long subjectId) {
        this.subjectId = subjectId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TeacherSubject that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
