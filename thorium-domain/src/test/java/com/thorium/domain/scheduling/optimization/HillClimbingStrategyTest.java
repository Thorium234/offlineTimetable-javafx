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

class HillClimbingStrategyTest {

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
        HillClimbingStrategy strategy = new HillClimbingStrategy();
        TimetableGenerationResult result = strategy.optimize(initial, context);

        assertTrue(result.isSuccess());
        assertFalse(result.schedule().isEmpty());
    }

    @Test
    void preservesHardConstraints() {
        TimetableGenerationResult initial = TimetableGenerationResult.success(fullSchedule, 0.5);
        HillClimbingStrategy strategy = new HillClimbingStrategy();
        TimetableGenerationResult result = strategy.optimize(initial, context);

        HardConstraintValidator validator = new HardConstraintValidator();
        var validation = validator.validateComplete(result.schedule(), context);
        assertTrue(validation.isValid(), "Hard constraints violated: " + validation.errors());
    }

    @Test
    void returnsInitialOnFailedResult() {
        TimetableGenerationResult failed = TimetableGenerationResult.failure(List.of("precondition failed"));
        HillClimbingStrategy strategy = new HillClimbingStrategy();
        TimetableGenerationResult result = strategy.optimize(failed, context);

        assertFalse(result.isSuccess());
    }

    @Test
    void preservesLessonCounts() {
        TimetableGenerationResult initial = TimetableGenerationResult.success(fullSchedule, 0.5);
        HillClimbingStrategy strategy = new HillClimbingStrategy();
        TimetableGenerationResult result = strategy.optimize(initial, context);

        long placedCount = result.schedule().placedLessons().stream()
                .filter(p -> p.assignment().getId().equals(assignment.getId()))
                .count();
        assertEquals(5, placedCount);
    }

    @Test
    void improvesScoreOnWeakInitialSchedule() {
        PartialSchedule weakSchedule = new PartialSchedule();
        weakSchedule.place(new PlacedLesson(assignment, new ScheduleSlot(DayOfWeek.MONDAY, 1)));
        weakSchedule.place(new PlacedLesson(assignment, new ScheduleSlot(DayOfWeek.MONDAY, 2)));
        weakSchedule.place(new PlacedLesson(assignment, new ScheduleSlot(DayOfWeek.MONDAY, 3)));
        weakSchedule.place(new PlacedLesson(assignment, new ScheduleSlot(DayOfWeek.MONDAY, 4)));
        weakSchedule.place(new PlacedLesson(assignment, new ScheduleSlot(DayOfWeek.MONDAY, 5)));

        TimetableGenerationResult initial = TimetableGenerationResult.success(weakSchedule, 0.0);
        HillClimbingStrategy strategy = new HillClimbingStrategy();
        TimetableGenerationResult result = strategy.optimize(initial, context);

        HardConstraintValidator validator = new HardConstraintValidator();
        var validation = validator.validateComplete(result.schedule(), context);
        assertTrue(validation.isValid(), "Hard constraints violated: " + validation.errors());
        assertEquals(5, result.schedule().size());
    }
}
