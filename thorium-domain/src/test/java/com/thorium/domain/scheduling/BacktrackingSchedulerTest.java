package com.thorium.domain.scheduling;

import com.thorium.domain.constraint.HardConstraintValidator;
import com.thorium.domain.constraint.SoftConstraintScorer;
import com.thorium.domain.model.*;
import com.thorium.domain.value.DayOfWeek;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BacktrackingSchedulerTest {

    private BacktrackingScheduler backtracker;
    private HardConstraintValidator hardValidator;
    private SoftConstraintScorer softScorer;

    @BeforeEach
    void setUp() {
        hardValidator = new HardConstraintValidator();
        softScorer = new SoftConstraintScorer();
        backtracker = new BacktrackingScheduler(hardValidator, softScorer);
    }

    @Test
    void resolvesCompleteScheduleForSimpleCase() {
        SchedulingContext context = SchedulingContext.builder()
                .assignments(List.of(new TeachingAssignment(1L, 1L, 1L, 1L, 3)))
                .teachers(List.of(new Teacher(1L, "T001", "John", true)))
                .subjects(List.of(new Subject(1L, "S001", "Math", true, 5, false, false, null)))
                .classStreams(List.of(new ClassStream(1L, "F1E", 1, "East", "Form 1 East")))
                .periodsPerDay(8)
                .build();

        TimetableGenerationResult result = backtracker.resolve(context, new PartialSchedule());
        assertTrue(result.isSuccess());
        assertEquals(3, result.schedule().size());
        assertTrue(result.qualityScore() > 0);
    }

    @Test
    void strictTierSucceedsWhenEnoughSlots() {
        SchedulingContext context = SchedulingContext.builder()
                .assignments(List.of(
                        new TeachingAssignment(1L, 1L, 1L, 1L, 3),
                        new TeachingAssignment(2L, 2L, 1L, 2L, 3)))
                .teachers(List.of(
                        new Teacher(1L, "T001", "John", true),
                        new Teacher(2L, "T002", "Jane", true)))
                .subjects(List.of(new Subject(1L, "S001", "Math", true, 5, false, false, null)))
                .classStreams(List.of(
                        new ClassStream(1L, "F1E", 1, "East", "Form 1 East"),
                        new ClassStream(2L, "F1W", 1, "West", "Form 1 West")))
                .periodsPerDay(8)
                .build();

        TimetableGenerationResult result = backtracker.resolve(context, new PartialSchedule());
        assertTrue(result.isSuccess());
        assertEquals(6, result.schedule().size());
    }

    @Test
    void relaxedTierHandlesImpossibleConstraints() {
        TeachingAssignment assignment = new TeachingAssignment(1L, 1L, 1L, 1L, 100);
        SchedulingContext context = SchedulingContext.builder()
                .assignments(List.of(assignment))
                .teachers(List.of(new Teacher(1L, "T001", "John", true)))
                .subjects(List.of(new Subject(1L, "S001", "Math", true, 5, false, false, null)))
                .classStreams(List.of(new ClassStream(1L, "F1E", 1, "East", "Form 1 East")))
                .periodsPerDay(8)
                .build();

        TimetableGenerationResult result = backtracker.resolve(context, new PartialSchedule());
        assertFalse(result.isSuccess());
        assertTrue(result.schedule().size() < 100);
    }

    @Test
    void continuesFromPartialSchedule() {
        TeachingAssignment assignment = new TeachingAssignment(1L, 1L, 1L, 1L, 5);
        SchedulingContext context = SchedulingContext.builder()
                .assignments(List.of(assignment))
                .teachers(List.of(new Teacher(1L, "T001", "John", true)))
                .subjects(List.of(new Subject(1L, "S001", "Math", true, 5, false, false, null)))
                .classStreams(List.of(new ClassStream(1L, "F1E", 1, "East", "Form 1 East")))
                .periodsPerDay(8)
                .build();

        PartialSchedule initial = new PartialSchedule();
        initial.place(new PlacedLesson(assignment, new ScheduleSlot(DayOfWeek.MONDAY, 1)));
        initial.place(new PlacedLesson(assignment, new ScheduleSlot(DayOfWeek.MONDAY, 2)));

        TimetableGenerationResult result = backtracker.resolve(context, initial);
        assertTrue(result.isSuccess());
        assertEquals(5, result.schedule().size());
    }

    @Test
    void respectsTeacherAvailability() {
        TeacherAvailability unavailable = new TeacherAvailability(
                1L, 1L, DayOfWeek.MONDAY, 1, false);
        TeachingAssignment assignment = new TeachingAssignment(1L, 1L, 1L, 1L, 3);

        SchedulingContext context = SchedulingContext.builder()
                .assignments(List.of(assignment))
                .teachers(List.of(new Teacher(1L, "T001", "John", true)))
                .subjects(List.of(new Subject(1L, "S001", "Math", true, 5, false, false, null)))
                .classStreams(List.of(new ClassStream(1L, "F1E", 1, "East", "Form 1 East")))
                .teacherAvailability(List.of(unavailable))
                .periodsPerDay(8)
                .build();

        TimetableGenerationResult result = backtracker.resolve(context, new PartialSchedule());
        assertTrue(result.isSuccess());
        boolean placedOnUnavailable = result.schedule().placedLessons().stream()
                .anyMatch(p -> p.slot().equals(new ScheduleSlot(DayOfWeek.MONDAY, 1)));
        assertFalse(placedOnUnavailable);
    }

    @Test
    void avoidsTeacherAndClassClashes() {
        SchedulingContext context = SchedulingContext.builder()
                .assignments(List.of(
                        new TeachingAssignment(1L, 1L, 1L, 1L, 5),
                        new TeachingAssignment(2L, 1L, 1L, 2L, 5)))
                .teachers(List.of(new Teacher(1L, "T001", "John", true)))
                .subjects(List.of(new Subject(1L, "S001", "Math", true, 5, false, false, null)))
                .classStreams(List.of(
                        new ClassStream(1L, "F1E", 1, "East", "Form 1 East"),
                        new ClassStream(2L, "F1W", 1, "West", "Form 1 West")))
                .periodsPerDay(8)
                .build();

        TimetableGenerationResult result = backtracker.resolve(context, new PartialSchedule());
        assertTrue(result.isSuccess());
        var lessons = result.schedule().placedLessons();
        for (var day : DayOfWeek.workingDays()) {
            for (int p = 1; p <= 8; p++) {
                ScheduleSlot slot = new ScheduleSlot(day, p);
                var atSlot = lessons.stream().filter(l -> l.slot().equals(slot)).toList();
                long distinctTeachers = atSlot.stream()
                        .map(l -> l.assignment().getTeacherId())
                        .distinct().count();
                assertEquals(atSlot.size(), distinctTeachers, "Teacher clash at " + slot);
            }
        }
    }

    @Test
    void handlesManyAssignments() {
        List<TeachingAssignment> assignments = new ArrayList<>();
        List<Teacher> teachers = new ArrayList<>();
        List<ClassStream> streams = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            teachers.add(new Teacher((long) i, "T" + i, "Teacher " + i, true));
            streams.add(new ClassStream((long) i, "C" + i, 1, "S" + i, "Class " + i));
            assignments.add(new TeachingAssignment((long) i, (long) i, 1L, (long) i, 3));
        }

        SchedulingContext context = SchedulingContext.builder()
                .assignments(assignments)
                .teachers(teachers)
                .subjects(List.of(new Subject(1L, "S001", "Math", true, 5, false, false, null)))
                .classStreams(streams)
                .periodsPerDay(8)
                .build();

        TimetableGenerationResult result = backtracker.resolve(context, new PartialSchedule());
        assertTrue(result.isSuccess());
        assertEquals(30, result.schedule().size());
    }

    @Test
    void strictTierEnforcesCbcNoDouble() {
        Subject cbcSubject = new Subject(1L, "S001", "CBC Subject", true, true, 5, false, false, null);
        TeachingAssignment assignment = new TeachingAssignment(1L, 1L, 1L, 1L, 4);

        SchedulingContext context = SchedulingContext.builder()
                .assignments(List.of(assignment))
                .teachers(List.of(new Teacher(1L, "T001", "John", true)))
                .subjects(List.of(cbcSubject))
                .classStreams(List.of(new ClassStream(1L, "F1E", 1, "East", "Form 1 East")))
                .periodsPerDay(8)
                .cbcNoDoubleLessonEnabled(true)
                .build();

        TimetableGenerationResult result = backtracker.resolve(context, new PartialSchedule());
        assertTrue(result.isSuccess());
        var lessons = result.schedule().placedLessons();
        for (var day : DayOfWeek.workingDays()) {
            var dayLessons = lessons.stream()
                    .filter(l -> l.slot().dayOfWeek() == day)
                    .sorted((a, b) -> Integer.compare(a.slot().periodNumber(), b.slot().periodNumber()))
                    .toList();
            for (int i = 1; i < dayLessons.size(); i++) {
                assertNotEquals(1,
                        dayLessons.get(i).slot().periodNumber() - dayLessons.get(i - 1).slot().periodNumber(),
                        "Consecutive CBC lessons on " + day);
            }
        }
    }

    @Test
    void passesCoreConstraintsCheck() {
        TeachingAssignment ta = new TeachingAssignment(1L, 1L, 1L, 1L, 3);
        SchedulingContext context = SchedulingContext.builder()
                .assignments(List.of(ta))
                .teachers(List.of(new Teacher(1L, "T001", "John", true)))
                .subjects(List.of(new Subject(1L, "S001", "Math", true, 5, false, false, null)))
                .classStreams(List.of(new ClassStream(1L, "F1E", 1, "East", "Form 1 East")))
                .periodsPerDay(8)
                .build();

        PartialSchedule schedule = new PartialSchedule();
        ScheduleSlot slot = new ScheduleSlot(DayOfWeek.MONDAY, 1);
        assertTrue(BacktrackingScheduler.passesCoreConstraints(ta, slot, schedule, context));

        schedule.place(new PlacedLesson(ta, slot));
        assertFalse(BacktrackingScheduler.passesCoreConstraints(ta, slot, schedule, context));
    }

    @Test
    void handlesDoublePeriodAssignments() {
        TeachingAssignment doubleTa = new TeachingAssignment(
                1L, 1L, 1L, 1L, 4, LessonDuration.DOUBLE);
        Subject doubleSubject = new Subject(1L, "S001", "Physics", true, 5, false, true, null);

        SchedulingContext context = SchedulingContext.builder()
                .assignments(List.of(doubleTa))
                .teachers(List.of(new Teacher(1L, "T001", "John", true)))
                .subjects(List.of(doubleSubject))
                .classStreams(List.of(new ClassStream(1L, "F1E", 1, "East", "Form 1 East")))
                .periodsPerDay(8)
                .build();

        TimetableGenerationResult result = backtracker.resolve(context, new PartialSchedule());
        assertTrue(result.isSuccess());
        assertEquals(4, result.schedule().size());

        var placed = result.schedule().placedLessons();
        for (int i = 0; i < placed.size(); i += 2) {
            assertEquals(placed.get(i).slot().dayOfWeek(), placed.get(i + 1).slot().dayOfWeek());
            assertEquals(1, placed.get(i + 1).slot().periodNumber() - placed.get(i).slot().periodNumber());
        }
    }
}
