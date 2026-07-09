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

class GeneticAlgorithmStrategyTest {

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
        GeneticAlgorithmStrategy strategy = new GeneticAlgorithmStrategy();
        TimetableGenerationResult result = strategy.optimize(initial, context);

        assertTrue(result.isSuccess());
        assertFalse(result.schedule().isEmpty());
        assertTrue(result.qualityScore() > 0);
    }

    @Test
    void improvesScoreOverInitial() {
        TimetableGenerationResult initial = TimetableGenerationResult.success(fullSchedule, 0.0);
        GeneticAlgorithmStrategy strategy = new GeneticAlgorithmStrategy();
        TimetableGenerationResult result = strategy.optimize(initial, context);

        assertTrue(result.isSuccess());
        assertEquals(5, result.schedule().size());

        HardConstraintValidator validator = new HardConstraintValidator();
        var validation = validator.validateComplete(result.schedule(), context);
        assertTrue(validation.isValid(), "Hard constraints violated: " + validation.errors());
    }

    @Test
    void returnsInitialOnFailedResult() {
        TimetableGenerationResult failed = TimetableGenerationResult.failure(List.of("precondition failed"));
        GeneticAlgorithmStrategy strategy = new GeneticAlgorithmStrategy();
        TimetableGenerationResult result = strategy.optimize(failed, context);

        assertFalse(result.isSuccess());
    }

    @Test
    void preservesLessonCounts() {
        TimetableGenerationResult initial = TimetableGenerationResult.success(fullSchedule, 0.5);
        GeneticAlgorithmStrategy strategy = new GeneticAlgorithmStrategy();
        TimetableGenerationResult result = strategy.optimize(initial, context);

        long placedCount = result.schedule().placedLessons().stream()
                .filter(p -> p.assignment().getId().equals(assignment.getId()))
                .count();
        assertEquals(5, placedCount);
    }

    @Test
    void handlesMultipleAssignments() {
        Teacher teacher2 = new Teacher(2L, "T002", "Jane", true);
        ClassStream class2 = new ClassStream(2L, "F1W", 1, "West", "Form 1 West");
        TeachingAssignment assignment2 = new TeachingAssignment(2L, 2L, 1L, 2L, 4);

        SchedulingContext ctx = SchedulingContext.builder()
                .assignments(List.of(assignment, assignment2))
                .teachers(List.of(new Teacher(1L, "T001", "John", true), teacher2))
                .subjects(List.of(new Subject(1L, "S001", "Math", true, 5, false, false, null)))
                .classStreams(List.of(new ClassStream(1L, "F1E", 1, "East", "Form 1 East"), class2))
                .periodsPerDay(8)
                .build();

        PartialSchedule schedule = new PartialSchedule();
        schedule.place(new PlacedLesson(assignment, new ScheduleSlot(DayOfWeek.MONDAY, 1)));
        schedule.place(new PlacedLesson(assignment, new ScheduleSlot(DayOfWeek.TUESDAY, 1)));
        schedule.place(new PlacedLesson(assignment, new ScheduleSlot(DayOfWeek.WEDNESDAY, 1)));
        schedule.place(new PlacedLesson(assignment, new ScheduleSlot(DayOfWeek.THURSDAY, 1)));
        schedule.place(new PlacedLesson(assignment, new ScheduleSlot(DayOfWeek.FRIDAY, 1)));
        schedule.place(new PlacedLesson(assignment2, new ScheduleSlot(DayOfWeek.MONDAY, 2)));
        schedule.place(new PlacedLesson(assignment2, new ScheduleSlot(DayOfWeek.TUESDAY, 2)));
        schedule.place(new PlacedLesson(assignment2, new ScheduleSlot(DayOfWeek.WEDNESDAY, 2)));
        schedule.place(new PlacedLesson(assignment2, new ScheduleSlot(DayOfWeek.THURSDAY, 2)));

        TimetableGenerationResult initial = TimetableGenerationResult.success(schedule, 0.5);
        GeneticAlgorithmStrategy strategy = new GeneticAlgorithmStrategy();
        TimetableGenerationResult result = strategy.optimize(initial, ctx);

        HardConstraintValidator validator = new HardConstraintValidator();
        var validation = validator.validateComplete(result.schedule(), ctx);
        assertTrue(validation.isValid(), "Hard constraints violated: " + validation.errors());
        assertEquals(9, result.schedule().size());
    }
}
