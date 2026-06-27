package com.thorium.application.port;

import com.thorium.domain.model.TeacherSubject;

import java.util.List;
import java.util.Optional;

public interface TeacherSubjectRepository {

    TeacherSubject save(TeacherSubject teacherSubject);

    Optional<TeacherSubject> findById(Long id);

    List<TeacherSubject> findAll();

    List<TeacherSubject> findByTeacherId(Long teacherId);

    List<TeacherSubject> findBySubjectId(Long subjectId);

    void deleteById(Long id);

    void deleteByTeacherIdAndSubjectId(Long teacherId, Long subjectId);

    long count();
}
