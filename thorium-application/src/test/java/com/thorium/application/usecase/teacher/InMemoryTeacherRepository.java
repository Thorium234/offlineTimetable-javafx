package com.thorium.application.usecase.teacher;

import com.thorium.application.port.TeacherRepository;
import com.thorium.domain.model.Teacher;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

class InMemoryTeacherRepository implements TeacherRepository {

    private final Map<Long, Teacher> store = new LinkedHashMap<>();
    private final AtomicLong idSequence = new AtomicLong(1);

    @Override
    public Teacher save(Teacher teacher) {
        if (teacher.getId() == null) {
            teacher.setId(idSequence.getAndIncrement());
        }
        store.put(teacher.getId(), copy(teacher));
        return copy(teacher);
    }

    @Override
    public Optional<Teacher> findById(Long id) {
        return Optional.ofNullable(store.get(id)).map(this::copy);
    }

    @Override
    public Optional<Teacher> findByCode(String code) {
        return store.values().stream()
                .filter(t -> t.getCode().equals(code))
                .findFirst()
                .map(this::copy);
    }

    @Override
    public List<Teacher> findAll() {
        return store.values().stream().map(this::copy).toList();
    }

    @Override
    public void deleteById(Long id) {
        store.remove(id);
    }

    @Override
    public long count() {
        return store.size();
    }

    private Teacher copy(Teacher source) {
        return new Teacher(
                source.getId(),
                source.getCode(),
                source.getName(),
                source.isActive(),
                source.getMaxLessonsPerDay(),
                source.getMaxLessonsPerWeek()
        );
    }
}
