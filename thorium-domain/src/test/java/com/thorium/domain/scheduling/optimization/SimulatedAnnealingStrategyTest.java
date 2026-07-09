package com.thorium.domain.scheduling.optimization;

import com.thorium.domain.constraint.HardConstraintValidator;
import com.thorium.domain.model.*;
import com.thorium.domain.scheduling.PartialSchedule;
import com.thorium.domain.scheduling.PlacedLesson;
import com.thorium.domain.scheduling.SchedulingContext;
import com.thorium.domain.scheduling.TimetableGenerationResult;
import com.thorium.domain.value.DayOfWeek;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SimulatedAnnealingStrategyTest {

    private SchedulingContext context;
    private TeachingAssignment assignment;
    private PartialSchedule fullSchedule;

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

        fullSchedule = new PartialSchedule();
        fullSchedule.place(new PlacedLesson(assignment, new ScheduleSlot(DayOfWeek.MONDAY, 1)));
        fullSchedule.place(new PlacedLesson(assignment, new ScheduleSlot(DayOfWeek.MONDAY, 2)));
        fullSchedule.place(new PlacedLesson(assignment, new ScheduleSlot(DayOfWeek.TUESDAY, 1)));
        fullSchedule.place(new PlacedLesson(assignment, new ScheduleSlot(DayOfWeek.THURSDAY, 1)));
        fullSchedule.place(new PlacedLesson(assignment, new ScheduleSlot(DayOfWeek.FRIDAY, 1)));
    }

    @Test
    void optimizesWhenGivenValidSchedule() {
        TimetableGenerationResult initial = TimetableGenerationResult.success(fullSchedule, 0.5);
        SimulatedAnnealingStrategy strategy = new SimulatedAnnealingStrategy();
        TimetableGenerationResult result = strategy.optimize(initial, context);

        assertTrue(result.isSuccess());
        assertFalse(result.schedule().isEmpty());
        assertTrue(result.qualityScore() > 0);
    }

    @Test
    void preservesHardConstraints() {
        TimetableGenerationResult initial = TimetableGenerationResult.success(fullSchedule, 0.5);
        SimulatedAnnealingStrategy strategy = new SimulatedAnnealingStrategy();
        TimetableGenerationResult result = strategy.optimize(initial, context);

        HardConstraintValidator validator = new HardConstraintValidator();
        var validation = validator.validateComplete(result.schedule(), context);
        assertTrue(validation.isValid(), "Hard constraints violated: " + validation.errors());
    }

    @Test
    void returnsInitialOnFailedResult() {
        TimetableGenerationResult failed = TimetableGenerationResult.failure(List.of("precondition failed"));
        SimulatedAnnealingStrategy strategy = new SimulatedAnnealingStrategy();
        TimetableGenerationResult result = strategy.optimize(failed, context);

        assertFalse(result.isSuccess());
    }

    @Test
    void preservesLessonCounts() {
        TimetableGenerationResult initial = TimetableGenerationResult.success(fullSchedule, 0.5);
        SimulatedAnnealingStrategy strategy = new SimulatedAnnealingStrategy();
        TimetableGenerationResult result = strategy.optimize(initial, context);

        long placedCount = result.schedule().placedLessons().stream()
                .filter(p -> p.assignment().getId().equals(assignment.getId()))
                .count();
        assertEquals(5, placedCount);
    }

    @Test
    void handlesAssignmentWithTeacherAvailability() {
        TeacherAvailability unavailable = new TeacherAvailability(
                1L, 1L, DayOfWeek.MONDAY, 1, false);
        SchedulingContext ctx = SchedulingContext.builder()
                .assignments(List.of(assignment))
                .teachers(List.of(new Teacher(1L, "T001", "John", true)))
                .subjects(List.of(new Subject(1L, "S001", "Math", true, 5, false, false, null)))
                .classStreams(List.of(new ClassStream(1L, "F1E", 1, "East", "Form 1 East")))
                .teacherAvailability(List.of(unavailable))
                .periodsPerDay(8)
                .build();

        PartialSchedule schedule = new PartialSchedule();
        schedule.place(new PlacedLesson(assignment, new ScheduleSlot(DayOfWeek.MONDAY, 2)));
        schedule.place(new PlacedLesson(assignment, new ScheduleSlot(DayOfWeek.TUESDAY, 1)));
        schedule.place(new PlacedLesson(assignment, new ScheduleSlot(DayOfWeek.WEDNESDAY, 1)));
        schedule.place(new PlacedLesson(assignment, new ScheduleSlot(DayOfWeek.THURSDAY, 1)));
        schedule.place(new PlacedLesson(assignment, new ScheduleSlot(DayOfWeek.FRIDAY, 1)));

        TimetableGenerationResult initial = TimetableGenerationResult.success(schedule, 0.5);
        SimulatedAnnealingStrategy strategy = new SimulatedAnnealingStrategy();
        TimetableGenerationResult result = strategy.optimize(initial, ctx);

        HardConstraintValidator validator = new HardConstraintValidator();
        var validation = validator.validateComplete(result.schedule(), ctx);
        assertTrue(validation.isValid(), "Hard constraints violated: " + validation.errors());

        boolean placedOnUnavailable = result.schedule().placedLessons().stream()
                .anyMatch(p -> p.slot().equals(new ScheduleSlot(DayOfWeek.MONDAY, 1)));
        assertFalse(placedOnUnavailable, "Should not place on unavailable slot");
    }
}
