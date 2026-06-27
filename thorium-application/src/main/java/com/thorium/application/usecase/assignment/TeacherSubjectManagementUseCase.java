package com.thorium.application.usecase.assignment;

import com.thorium.application.dto.SubjectDto;
import com.thorium.application.dto.TeacherSubjectDto;
import com.thorium.application.port.SubjectRepository;
import com.thorium.application.port.TeacherRepository;
import com.thorium.application.port.TeacherSubjectRepository;
import com.thorium.domain.model.TeacherSubject;

import java.util.List;

public class TeacherSubjectManagementUseCase {

    private final TeacherSubjectRepository teacherSubjectRepository;
    private final TeacherRepository teacherRepository;
    private final SubjectRepository subjectRepository;

    public TeacherSubjectManagementUseCase(TeacherSubjectRepository teacherSubjectRepository,
                                           TeacherRepository teacherRepository,
                                           SubjectRepository subjectRepository) {
        this.teacherSubjectRepository = teacherSubjectRepository;
        this.teacherRepository = teacherRepository;
        this.subjectRepository = subjectRepository;
    }

    public TeacherSubjectDto assign(Long teacherId, Long subjectId) {
        if (teacherId == null || subjectId == null) {
            throw new IllegalArgumentException("Teacher and subject are required");
        }
        teacherRepository.findById(teacherId)
                .orElseThrow(() -> new IllegalArgumentException("Teacher not found"));
        subjectRepository.findById(subjectId)
                .orElseThrow(() -> new IllegalArgumentException("Subject not found"));

        List<TeacherSubject> existing = teacherSubjectRepository.findByTeacherId(teacherId);
        boolean alreadyAssigned = existing.stream().anyMatch(ts -> ts.getSubjectId().equals(subjectId));
        if (alreadyAssigned) {
            throw new IllegalArgumentException("Subject already assigned to this teacher");
        }

        TeacherSubject ts = new TeacherSubject(null, teacherId, subjectId);
        return toDto(teacherSubjectRepository.save(ts));
    }

    public void unassign(Long teacherId, Long subjectId) {
        teacherSubjectRepository.deleteByTeacherIdAndSubjectId(teacherId, subjectId);
    }

    public void delete(Long id) {
        teacherSubjectRepository.deleteById(id);
    }

    public List<TeacherSubjectDto> findByTeacherId(Long teacherId) {
        return teacherSubjectRepository.findByTeacherId(teacherId).stream().map(this::toDto).toList();
    }

    public List<SubjectDto> findSubjectsByTeacherId(Long teacherId) {
        return teacherSubjectRepository.findByTeacherId(teacherId).stream()
                .map(ts -> subjectRepository.findById(ts.getSubjectId()).orElse(null))
                .filter(s -> s != null)
                .map(s -> new SubjectDto(s.getId(), s.getCode(), s.getName(), s.isExaminable(),
                        s.getCbcDefaultLessons(), s.isAllowsDoublePeriod(), s.isRequiresDoublePeriod(), s.getColor()))
                .toList();
    }

    public List<TeacherSubjectDto> findAll() {
        return teacherSubjectRepository.findAll().stream().map(this::toDto).toList();
    }

    public long count() {
        return teacherSubjectRepository.count();
    }

    private TeacherSubjectDto toDto(TeacherSubject ts) {
        String teacherName = teacherRepository.findById(ts.getTeacherId())
                .map(t -> t.getName()).orElse("Unknown");
        String subjectName = subjectRepository.findById(ts.getSubjectId())
                .map(s -> s.getName()).orElse("Unknown");
        String subjectCode = subjectRepository.findById(ts.getSubjectId())
                .map(s -> s.getCode()).orElse("Unknown");
        return new TeacherSubjectDto(
                ts.getId(), ts.getTeacherId(), teacherName,
                ts.getSubjectId(), subjectName, subjectCode
        );
    }
}
