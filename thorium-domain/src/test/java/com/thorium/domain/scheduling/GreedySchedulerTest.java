package com.thorium.domain.scheduling;

import com.thorium.domain.constraint.HardConstraintValidator;
import com.thorium.domain.constraint.SoftConstraintScorer;
import com.thorium.domain.model.*;
import com.thorium.domain.value.DayOfWeek;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GreedySchedulerTest {

    private GreedyScheduler scheduler;
    private HardConstraintValidator hardValidator;
    private SoftConstraintScorer softScorer;

    @BeforeEach
    void setUp() {
        hardValidator = new HardConstraintValidator();
        softScorer = new SoftConstraintScorer();
        scheduler = new GreedyScheduler(hardValidator, softScorer);
    }

    @Test
    void schedulesAllLessonsForSimpleCase() {
        SchedulingContext context = SchedulingContext.builder()
                .assignments(List.of(new TeachingAssignment(1L, 1L, 1L, 1L, 5)))
                .teachers(List.of(new Teacher(1L, "T001", "John", true)))
                .subjects(List.of(new Subject(1L, "S001", "Math", true, 5, false, false, null)))
                .classStreams(List.of(new ClassStream(1L, "F1E", 1, "East", "Form 1 East")))
                .periodsPerDay(8)
                .build();

        PartialSchedule result = scheduler.schedule(context);
        assertEquals(5, result.size());
        assertFalse(result.placedLessons().stream()
                .anyMatch(p -> p.slot().periodNumber() > 8));
    }

    @Test
    void respectsTeacherUnavailability() {
        TeacherAvailability unavailable = new TeacherAvailability(
                1L, 1L, DayOfWeek.MONDAY, 1, false);

        SchedulingContext context = SchedulingContext.builder()
                .assignments(List.of(new TeachingAssignment(1L, 1L, 1L, 1L, 5)))
                .teachers(List.of(new Teacher(1L, "T001", "John", true)))
                .subjects(List.of(new Subject(1L, "S001", "Math", true, 5, false, false, null)))
                .classStreams(List.of(new ClassStream(1L, "F1E", 1, "East", "Form 1 East")))
                .teacherAvailability(List.of(unavailable))
                .periodsPerDay(8)
                .build();

        PartialSchedule result = scheduler.schedule(context);
        assertEquals(5, result.size());
        boolean placedOnUnavailable = result.placedLessons().stream()
                .anyMatch(p -> p.slot().equals(new ScheduleSlot(DayOfWeek.MONDAY, 1)));
        assertFalse(placedOnUnavailable);
    }

    @Test
    void handlesDoublePeriodAssignments() {
        TeachingAssignment doubleAssignment = new TeachingAssignment(
                1L, 1L, 1L, 1L, 4, LessonDuration.DOUBLE);

        Subject doubleSubject = new Subject(1L, "S001", "Physics", true, 5, true, true, null);

        SchedulingContext context = SchedulingContext.builder()
                .assignments(List.of(doubleAssignment))
                .teachers(List.of(new Teacher(1L, "T001", "John", true)))
                .subjects(List.of(doubleSubject))
                .classStreams(List.of(new ClassStream(1L, "F1E", 1, "East", "Form 1 East")))
                .periodsPerDay(8)
                .build();

        PartialSchedule result = scheduler.schedule(context);
        assertEquals(4, result.size()); // 2 pairs * 2 = 4 lessons

        // Check each pair is consecutive
        var placed = result.placedLessons();
        for (int i = 0; i < placed.size(); i += 2) {
            assertEquals(placed.get(i).slot().dayOfWeek(), placed.get(i + 1).slot().dayOfWeek());
            assertEquals(1, placed.get(i + 1).slot().periodNumber() - placed.get(i).slot().periodNumber());
        }
    }

    @Test
    void expandAssignmentsCreatesCorrectWorkItems() {
        TeachingAssignment single = new TeachingAssignment(1L, 1L, 1L, 1L, 5);
        TeachingAssignment doubleTa = new TeachingAssignment(
                2L, 1L, 2L, 1L, 4, LessonDuration.DOUBLE);

        SchedulingContext context = SchedulingContext.builder()
                .assignments(List.of(single, doubleTa))
                .teachers(List.of(new Teacher(1L, "T001", "John", true)))
                .subjects(List.of(
                        new Subject(1L, "S001", "Math", true, 5, false, false, null),
                        new Subject(2L, "S002", "Physics", true, 5, true, true, null)))
                .classStreams(List.of(new ClassStream(1L, "F1E", 1, "East", "Form 1 East")))
                .periodsPerDay(8)
                .build();

        List<GreedyScheduler.AssignmentWorkItem> items = scheduler.expandAssignments(context);
        assertEquals(7, items.size()); // 5 single + 2 double

        long singleItemCount = items.stream()
                .filter(i -> !i.requiresConsecutive())
                .count();
        long doubleItemCount = items.stream()
                .filter(GreedyScheduler.AssignmentWorkItem::requiresConsecutive)
                .count();
        assertEquals(5, singleItemCount);
        assertEquals(2, doubleItemCount);
    }

    @Test
    void multipleTeachersNoClashes() {
        Teacher teacher1 = new Teacher(1L, "T001", "John", true);
        Teacher teacher2 = new Teacher(2L, "T002", "Jane", true);
        ClassStream class1 = new ClassStream(1L, "F1E", 1, "East", "Form 1 East");
        ClassStream class2 = new ClassStream(2L, "F1W", 1, "West", "Form 1 West");

        SchedulingContext context = SchedulingContext.builder()
                .assignments(List.of(
                        new TeachingAssignment(1L, 1L, 1L, 1L, 3),
                        new TeachingAssignment(2L, 2L, 1L, 2L, 3)))
                .teachers(List.of(teacher1, teacher2))
                .subjects(List.of(new Subject(1L, "S001", "Math", true, 5, false, false, null)))
                .classStreams(List.of(class1, class2))
                .periodsPerDay(8)
                .build();

        PartialSchedule result = scheduler.schedule(context);
        assertEquals(6, result.size());

        // Verify no teacher clashes
        for (var day : DayOfWeek.workingDays()) {
            for (int p = 1; p <= 8; p++) {
                ScheduleSlot slot = new ScheduleSlot(day, p);
                long teacherCount = result.placedLessons().stream()
                        .filter(pl -> pl.slot().equals(slot))
                        .map(pl -> pl.assignment().getTeacherId())
                        .distinct()
                        .count();
                assertTrue(teacherCount <= 1, "Teacher clash at " + slot);
            }
        }
    }

    @Test
    void handlesTightConstraints() {
        // Teacher is unavailable for most slots
        TeachingAssignment assignment = new TeachingAssignment(1L, 1L, 1L, 1L, 2);
        List<TeacherAvailability> unavailability = DayOfWeek.workingDays().stream()
                .flatMap(day -> {
                    TeacherAvailability[] arr = new TeacherAvailability[7];
                    for (int p = 1; p <= 7; p++) {
                        arr[p - 1] = new TeacherAvailability(
                                (long) (day.ordinal() * 10 + p), 1L, day, p, false);
                    }
                    return java.util.Arrays.stream(arr);
                })
                .filter(a -> !(a.getDayOfWeek() == DayOfWeek.FRIDAY && a.getPeriodNumber() == 1))
                .filter(a -> !(a.getDayOfWeek() == DayOfWeek.FRIDAY && a.getPeriodNumber() == 2))
                .toList();

        SchedulingContext context = SchedulingContext.builder()
                .assignments(List.of(assignment))
                .teachers(List.of(new Teacher(1L, "T001", "John", true)))
                .subjects(List.of(new Subject(1L, "S001", "Math", true, 5, false, false, null)))
                .classStreams(List.of(new ClassStream(1L, "F1E", 1, "East", "Form 1 East")))
                .teacherAvailability(unavailability)
                .periodsPerDay(8)
                .build();

        PartialSchedule result = scheduler.schedule(context);
        assertEquals(2, result.size());
        assertTrue(result.placedLessons().stream()
                .allMatch(p -> p.slot().dayOfWeek() == DayOfWeek.FRIDAY));
    }
}
