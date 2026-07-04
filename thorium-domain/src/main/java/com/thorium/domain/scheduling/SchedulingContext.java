package com.thorium.domain.scheduling;

import com.thorium.domain.constraint.SoftConstraintScorer;
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
    private final SoftConstraintScorer softConstraintScorer;
    private final Map<Long, Room> roomsById;
    private final List<Room> rooms;

    private SchedulingContext(Builder builder) {
        this.assignments = copyOrWrap(builder.assignments);
        this.teachersById = copyOrWrapMap(builder.teachersById);
        this.subjectsById = copyOrWrapMap(builder.subjectsById);
        this.classStreamsById = copyOrWrapMap(builder.classStreamsById);
        this.unavailableByTeacher = copyOrWrapMapOfSets(builder.unavailableByTeacher);
        this.workingDays = copyOrWrap(builder.workingDays);
        this.periodsPerDay = builder.periodsPerDay;
        this.lessonPeriodNumbers = copyOrWrap(builder.lessonPeriodNumbers);
        this.cbcNoDoubleLessonEnabled = builder.cbcNoDoubleLessonEnabled;
        this.softConstraintScorer = builder.softConstraintScorer != null
                ? builder.softConstraintScorer
                : new SoftConstraintScorer();
        this.roomsById = copyOrWrapMap(builder.roomsById);
        this.rooms = List.copyOf(builder.rooms);
    }

    private static <T> List<T> copyOrWrap(List<T> list) {
        return list instanceof List<T> l && l.getClass() != ArrayList.class ? l : List.copyOf(list);
    }

    private static <K, V> Map<K, V> copyOrWrapMap(Map<K, V> map) {
        return map instanceof Map<K, V> m && m.getClass() != HashMap.class ? m : Map.copyOf(map);
    }

    private static <K> Map<K, Set<ScheduleSlot>> copyOrWrapMapOfSets(Map<K, Set<ScheduleSlot>> map) {
        if (map instanceof Map<?, ?> m && m.getClass() != HashMap.class) {
            Map<K, Set<ScheduleSlot>> result = new HashMap<>();
            for (var entry : map.entrySet()) {
                result.put(entry.getKey(), entry.getValue() instanceof Set<?> s && s.getClass() != HashSet.class
                        ? entry.getValue() : new HashSet<>(entry.getValue()));
            }
            return result;
        }
        Map<K, Set<ScheduleSlot>> result = new HashMap<>();
        for (var entry : map.entrySet()) {
            result.put(entry.getKey(), new HashSet<>(entry.getValue()));
        }
        return result;
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

    public SoftConstraintScorer softConstraintScorer() {
        return softConstraintScorer;
    }

    public List<Room> rooms() {
        return rooms;
    }

    public Optional<Room> roomById(Long id) {
        return Optional.ofNullable(roomsById.get(id));
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

    public static SchedulingContext dummy() {
        return dummyContext;
    }

    private static final SchedulingContext dummyContext = createDummy();

    private static SchedulingContext createDummy() {
        try {
            return builder()
                    .assignments(List.of(new TeachingAssignment(-1L, -1L, -1L, -1L, 1)))
                    .teachers(List.of(new Teacher(-1L, "X", "Dummy", true)))
                    .subjects(List.of(new Subject(-1L, "X", "Dummy", false, 1, false, false, null)))
                    .classStreams(List.of(new ClassStream(-1L, "X", 0, "X", "Dummy")))
                    .periodsPerDay(8)
                    .build();
        } catch (Exception e) {
            return null;
        }
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
        private SoftConstraintScorer softConstraintScorer;
        private Map<Long, Room> roomsById = Map.of();
        private List<Room> rooms = List.of();

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

        public Builder softConstraintScorer(SoftConstraintScorer scorer) {
            this.softConstraintScorer = scorer;
            return this;
        }

        public Builder rooms(List<Room> rooms) {
            this.rooms = rooms != null ? rooms : List.of();
            Map<Long, Room> map = new HashMap<>();
            for (Room room : this.rooms) {
                map.put(room.getId(), room);
            }
            this.roomsById = map;
            return this;
        }

        public Builder schoolSettings(SchoolSettings settings) {
            if (settings != null) {
                this.periodsPerDay = settings.getTotalPeriods();
                this.lessonPeriodNumbers = defaultLessonPeriodNumbers(settings.getTotalPeriods());
            }
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
