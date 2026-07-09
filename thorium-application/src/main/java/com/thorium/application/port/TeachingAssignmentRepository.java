package com.thorium.application.port;

import com.thorium.domain.model.TeachingAssignment;

import java.util.List;
import java.util.Optional;

public interface TeachingAssignmentRepository {

    TeachingAssignment save(TeachingAssignment assignment);

    Optional<TeachingAssignment> findById(Long id);

    List<TeachingAssignment> findAll();

    List<TeachingAssignment> findByTeacherId(Long teacherId);

    List<TeachingAssignment> findByClassStreamId(Long classStreamId);

    void deleteById(Long id);

    long count();
}
