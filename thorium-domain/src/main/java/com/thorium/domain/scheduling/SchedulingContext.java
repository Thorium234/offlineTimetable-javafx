package com.thorium.domain.scheduling;

import com.thorium.domain.model.*;
import com.thorium.domain.value.ConstraintType;
import com.thorium.domain.value.DayOfWeek;

import java.util.*;

public final class SchedulingContext {

    private final List<TeachingAssignment> assignments;
    private final Map<Long, Teacher> teachersById;
    private final Map<Long, Subject> subjectsById;
    private final Map<Long, ClassStream> classStreamsById;
    private final Map<Long, Set<ScheduleSlot>> unavailableByTeacher;
    private final List<DayOfWeek> workingDays;
    private final int periodsPerDay;
    private final List<Integer> lessonPeriodNumbers;
    private final boolean cbcNoDoubleLessonEnabled;

    private SchedulingContext(Builder builder) {
        this.assignments = List.copyOf(builder.assignments);
        this.teachersById = Map.copyOf(builder.teachersById);
        this.subjectsById = Map.copyOf(builder.subjectsById);
        this.classStreamsById = Map.copyOf(builder.classStreamsById);
        this.unavailableByTeacher = Map.copyOf(builder.unavailableByTeacher);
        this.workingDays = List.copyOf(builder.workingDays);
        this.periodsPerDay = builder.periodsPerDay;
        this.lessonPeriodNumbers = List.copyOf(builder.lessonPeriodNumbers);
        this.cbcNoDoubleLessonEnabled = builder.cbcNoDoubleLessonEnabled;
    }

    public List<TeachingAssignment> assignments() {
        return assignments;
    }

    public Optional<Teacher> teacher(long id) {
        return Optional.ofNullable(teachersById.get(id));
    }

    public Optional<Subject> subject(long id) {
        return Optional.ofNullable(subjectsById.get(id));
    }

    public Optional<ClassStream> classStream(long id) {
        return Optional.ofNullable(classStreamsById.get(id));
    }

    public boolean isTeacherUnavailable(long teacherId, ScheduleSlot slot) {
        Set<ScheduleSlot> slots = unavailableByTeacher.get(teacherId);
        return slots != null && slots.contains(slot);
    }

    public List<DayOfWeek> workingDays() {
        return workingDays;
    }

    public int periodsPerDay() {
        return periodsPerDay;
    }

    public List<Integer> lessonPeriodNumbers() {
        return lessonPeriodNumbers;
    }

    public int indexOfLessonPeriod(int periodNumber) {
        return lessonPeriodNumbers.indexOf(periodNumber);
    }

    public boolean isCbcNoDoubleLessonEnabled() {
        return cbcNoDoubleLessonEnabled;
    }

    public List<ScheduleSlot> allSlots() {
        List<ScheduleSlot> slots = new ArrayList<>();
        for (DayOfWeek day : workingDays) {
            for (int pn : lessonPeriodNumbers) {
                slots.add(new ScheduleSlot(day, pn));
            }
        }
        return slots;
    }

    public int totalSlots() {
        return workingDays.size() * periodsPerDay;
    }

    public ScheduleSlot nextLessonSlot(ScheduleSlot slot) {
        int idx = lessonPeriodNumbers.indexOf(slot.periodNumber());
        if (idx < 0 || idx >= lessonPeriodNumbers.size() - 1) return null;
        return new ScheduleSlot(slot.dayOfWeek(), lessonPeriodNumbers.get(idx + 1));
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private List<TeachingAssignment> assignments = List.of();
        private Map<Long, Teacher> teachersById = Map.of();
        private Map<Long, Subject> subjectsById = Map.of();
        private Map<Long, ClassStream> classStreamsById = Map.of();
        private Map<Long, Set<ScheduleSlot>> unavailableByTeacher = Map.of();
        private List<DayOfWeek> workingDays = DayOfWeek.workingDays();
        private List<Integer> lessonPeriodNumbers = defaultLessonPeriodNumbers(8);
        private int periodsPerDay = 8;
        private boolean cbcNoDoubleLessonEnabled = false;

        private static List<Integer> defaultLessonPeriodNumbers(int count) {
            List<Integer> list = new ArrayList<>();
            for (int i = 1; i <= count; i++) list.add(i);
            return list;
        }

        public Builder assignments(List<TeachingAssignment> assignments) {
            this.assignments = assignments;
            return this;
        }

        public Builder teachers(List<Teacher> teachers) {
            Map<Long, Teacher> map = new HashMap<>();
            for (Teacher teacher : teachers) {
                map.put(teacher.getId(), teacher);
            }
            this.teachersById = map;
            return this;
        }

        public Builder subjects(List<Subject> subjects) {
            Map<Long, Subject> map = new HashMap<>();
            for (Subject subject : subjects) {
                map.put(subject.getId(), subject);
            }
            this.subjectsById = map;
            return this;
        }

        public Builder classStreams(List<ClassStream> classStreams) {
            Map<Long, ClassStream> map = new HashMap<>();
            for (ClassStream cs : classStreams) {
                map.put(cs.getId(), cs);
            }
            this.classStreamsById = map;
            return this;
        }

        public Builder teacherAvailability(List<TeacherAvailability> availability) {
            Map<Long, Set<ScheduleSlot>> map = new HashMap<>();
            for (TeacherAvailability entry : availability) {
                if (!entry.isAvailable()) {
                    map.computeIfAbsent(entry.getTeacherId(), k -> new HashSet<>())
                            .add(entry.toSlot());
                }
            }
            this.unavailableByTeacher = map;
            return this;
        }

        public Builder workingDays(List<DayOfWeek> workingDays) {
            this.workingDays = workingDays;
            return this;
        }

        public Builder lessonPeriodNumbers(List<Integer> periodNumbers) {
            this.lessonPeriodNumbers = List.copyOf(periodNumbers);
            this.periodsPerDay = periodNumbers.size();
            return this;
        }

        public Builder periodsPerDay(int periodsPerDay) {
            this.periodsPerDay = periodsPerDay;
            this.lessonPeriodNumbers = defaultLessonPeriodNumbers(periodsPerDay);
            return this;
        }

        public Builder cbcNoDoubleLessonEnabled(boolean enabled) {
            this.cbcNoDoubleLessonEnabled = enabled;
            return this;
        }

        public Builder constraints(List<Constraint> constraints) {
            for (Constraint constraint : constraints) {
                if (constraint.getConstraintType() == ConstraintType.CBC_NO_DOUBLE_LESSON) {
                    this.cbcNoDoubleLessonEnabled = constraint.isEnabled();
                }
            }
            return this;
        }

        public SchedulingContext build() {
            if (assignments.isEmpty()) {
                throw new IllegalStateException("At least one teaching assignment is required");
            }
            if (periodsPerDay <= 0) {
                throw new IllegalStateException("periodsPerDay must be positive");
            }
            return new SchedulingContext(this);
        }
    }
}
