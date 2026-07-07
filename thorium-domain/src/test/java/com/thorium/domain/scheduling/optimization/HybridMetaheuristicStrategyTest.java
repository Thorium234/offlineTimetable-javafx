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

class HybridMetaheuristicStrategyTest {

    private SchedulingContext simpleContext;
    private TeachingAssignment assignment;
    private PartialSchedule fullSchedule;

    @BeforeEach
    void setUp() {
        Teacher teacher = new Teacher(1L, "T001", "John", true);
        Subject subject = new Subject(1L, "S001", "Math", true, 5, false, false, null);
        ClassStream classStream = new ClassStream(1L, "F1E", 1, "East", "Form 1 East");
        assignment = new TeachingAssignment(1L, 1L, 1L, 1L, 5);

        simpleContext = SchedulingContext.builder()
                .assignments(List.of(assignment))
                .teachers(List.of(teacher))
                .subjects(List.of(subject))
                .classStreams(List.of(classStream))
                .periodsPerDay(8)
                .build();

        fullSchedule = new PartialSchedule();
        fullSchedule.place(new PlacedLesson(assignment, new ScheduleSlot(DayOfWeek.MONDAY, 1)));
        fullSchedule.place(new PlacedLesson(assignment, new ScheduleSlot(DayOfWeek.TUESDAY, 1)));
        fullSchedule.place(new PlacedLesson(assignment, new ScheduleSlot(DayOfWeek.WEDNESDAY, 1)));
        fullSchedule.place(new PlacedLesson(assignment, new ScheduleSlot(DayOfWeek.THURSDAY, 1)));
        fullSchedule.place(new PlacedLesson(assignment, new ScheduleSlot(DayOfWeek.FRIDAY, 1)));
    }

    @Test
    void optimizesWhenGivenValidSchedule() {
        TimetableGenerationResult initial = TimetableGenerationResult.success(fullSchedule, 0.5);
        HybridMetaheuristicStrategy strategy = new HybridMetaheuristicStrategy();
        TimetableGenerationResult result = strategy.optimize(initial, simpleContext);

        assertTrue(result.isSuccess());
        assertFalse(result.schedule().isEmpty());
        assertTrue(result.qualityScore() >= 0);
    }

    @Test
    void returnsValidScheduleAfterOptimization() {
        TimetableGenerationResult initial = TimetableGenerationResult.success(fullSchedule, 0.5);
        HybridMetaheuristicStrategy strategy = new HybridMetaheuristicStrategy();
        TimetableGenerationResult result = strategy.optimize(initial, simpleContext);

        HardConstraintValidator validator = new HardConstraintValidator();
        var validation = validator.validateComplete(result.schedule(), simpleContext);
        assertTrue(validation.isValid(), "Hard constraints violated: " + validation.errors());
    }

    @Test
    void preservesLessonCounts() {
        TimetableGenerationResult initial = TimetableGenerationResult.success(fullSchedule, 0.5);
        HybridMetaheuristicStrategy strategy = new HybridMetaheuristicStrategy();
        TimetableGenerationResult result = strategy.optimize(initial, simpleContext);

        long placedCount = result.schedule().placedLessons().stream()
                .filter(p -> p.assignment().getId().equals(assignment.getId()))
                .count();
        assertEquals(5, placedCount);
    }

    @Test
    void returnsInitialOnFailedResult() {
        TimetableGenerationResult failed = TimetableGenerationResult.failure(List.of("precondition failed"));
        HybridMetaheuristicStrategy strategy = new HybridMetaheuristicStrategy();
        TimetableGenerationResult result = strategy.optimize(failed, simpleContext);

        assertFalse(result.isSuccess());
    }

    @Test
    void generatesSpreadSchedule() {
        TimetableGenerationResult initial = TimetableGenerationResult.success(fullSchedule, 0.5);
        HybridMetaheuristicStrategy strategy = new HybridMetaheuristicStrategy();
        TimetableGenerationResult result = strategy.optimize(initial, simpleContext);

        long distinctDays = result.schedule().placedLessons().stream()
                .map(p -> p.slot().dayOfWeek())
                .distinct()
                .count();
        assertTrue(distinctDays >= 3, "Lessons should be spread across at least 3 days, got " + distinctDays);
    }
}
