package com.thorium.domain.scheduling.optimization;

import com.thorium.domain.constraint.SoftConstraintScorer;
import com.thorium.domain.model.*;
import com.thorium.domain.scheduling.PartialSchedule;
import com.thorium.domain.scheduling.PlacedLesson;
import com.thorium.domain.scheduling.SchedulingContext;
import com.thorium.domain.value.DayOfWeek;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SchedulingMoveUtilsTest {

    private SchedulingContext context;
    private TeachingAssignment assignment;
    private PartialSchedule schedule;

    @BeforeEach
    void setUp() {
        Teacher teacher = new Teacher(1L, "T001", "John", true);
        Subject subject = new Subject(1L, "S001", "Math", true, 5, false, false, null);
        ClassStream classStream = new ClassStream(1L, "F1E", 1, "East", "Form 1 East");
        assignment = new TeachingAssignment(1L, 1L, 1L, 1L, 5);

        context = SchedulingContext.builder()
                .assignments(List.of(assignment))
                .teachers(List.of(teacher))
                .subjects(List.of(subject))
                .classStreams(List.of(classStream))
                .periodsPerDay(8)
                .build();

        schedule = new PartialSchedule();
        schedule.place(new PlacedLesson(assignment, new ScheduleSlot(DayOfWeek.MONDAY, 1)));
        schedule.place(new PlacedLesson(assignment, new ScheduleSlot(DayOfWeek.TUESDAY, 1)));
        schedule.place(new PlacedLesson(assignment, new ScheduleSlot(DayOfWeek.WEDNESDAY, 1)));
        schedule.place(new PlacedLesson(assignment, new ScheduleSlot(DayOfWeek.THURSDAY, 1)));
        schedule.place(new PlacedLesson(assignment, new ScheduleSlot(DayOfWeek.FRIDAY, 1)));
    }

    @Test
    void countsValidReturnsTrueForCompleteSchedule() {
        assertTrue(SchedulingMoveUtils.countsValid(schedule, context));
    }

    @Test
    void countsValidReturnsFalseForIncompleteSchedule() {
        PartialSchedule incomplete = new PartialSchedule();
        incomplete.place(new PlacedLesson(assignment, new ScheduleSlot(DayOfWeek.MONDAY, 1)));
        assertFalse(SchedulingMoveUtils.countsValid(incomplete, context));
    }

    @Test
    void countsValidReturnsFalseForOverCountedSchedule() {
        PartialSchedule over = new PartialSchedule();
        for (int i = 0; i < 6; i++) {
            over.place(new PlacedLesson(assignment, new ScheduleSlot(DayOfWeek.MONDAY, i + 1)));
        }
        assertFalse(SchedulingMoveUtils.countsValid(over, context));
    }

    @Test
    void applyMoveSwapsTwoLessons() {
        List<PlacedLesson> lessons = schedule.placedLessons();
        PlacedLesson l1 = lessons.get(0);
        PlacedLesson l2 = lessons.get(2);

        var move = new SchedulingMove(0, 2, l2.slot(), l1.slot());
        PartialSchedule result = SchedulingMoveUtils.applyMove(move, schedule);

        assertEquals(l1.assignment(), result.placedLessons().get(2).assignment());
        assertEquals(l2.assignment(), result.placedLessons().get(0).assignment());
    }

    @Test
    void evaluateMoveReturnsScore() {
        List<PlacedLesson> lessons = schedule.placedLessons();
        PlacedLesson l1 = lessons.get(0);
        PlacedLesson l2 = lessons.get(2);

        var move = new SchedulingMove(0, 2, l2.slot(), l1.slot());
        double score = SchedulingMoveUtils.evaluateMove(move, schedule, context, new SoftConstraintScorer());
        assertTrue(score >= 0);
    }
}
