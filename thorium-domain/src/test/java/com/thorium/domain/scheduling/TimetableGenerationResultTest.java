package com.thorium.domain.scheduling;

import com.thorium.domain.model.*;
import com.thorium.domain.value.DayOfWeek;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TimetableGenerationResultTest {

    private final TeachingAssignment assignment = new TeachingAssignment(1L, 1L, 1L, 1L, 5);

    @Test
    void successResultHasCorrectState() {
        PartialSchedule schedule = new PartialSchedule();
        schedule.place(new PlacedLesson(assignment, new ScheduleSlot(DayOfWeek.MONDAY, 1)));
        TimetableGenerationResult result = TimetableGenerationResult.success(schedule, 0.85);
        assertTrue(result.isSuccess());
        assertEquals(0.85, result.qualityScore());
        assertEquals(schedule, result.schedule());
        assertTrue(result.errors().isEmpty());
        assertTrue(result.warnings().isEmpty());
    }

    @Test
    void failureResultHasErrors() {
        TimetableGenerationResult result = TimetableGenerationResult.failure(
                List.of("No teachers available"));
        assertFalse(result.isSuccess());
        assertEquals(0.0, result.qualityScore());
        assertEquals(1, result.errors().size());
        assertTrue(result.errors().getFirst().contains("No teachers"));
    }

    @Test
    void partialResultHasWarnings() {
        PartialSchedule schedule = new PartialSchedule();
        schedule.place(new PlacedLesson(assignment, new ScheduleSlot(DayOfWeek.MONDAY, 1)));
        TimetableGenerationResult result = TimetableGenerationResult.partial(
                schedule, 0.5, List.of("Incomplete schedule"));
        assertFalse(result.isSuccess());
        assertEquals(1, result.warnings().size());
    }

    @Test
    void isCompleteReturnsTrueWhenAllLessonsPlaced() {
        SchedulingContext context = SchedulingContext.builder()
                .assignments(List.of(assignment))
                .teachers(List.of(new Teacher(1L, "T001", "John", true)))
                .subjects(List.of(new Subject(1L, "S001", "Math", true, 5, false, false, null)))
                .classStreams(List.of(new ClassStream(1L, "F1E", 1, "East", "Form 1 East")))
                .periodsPerDay(8)
                .build();
        PartialSchedule schedule = new PartialSchedule();
        for (int i = 0; i < 5; i++) {
            schedule.place(new PlacedLesson(assignment,
                    new ScheduleSlot(DayOfWeek.workingDays().get(i % 5), 1)));
        }
        TimetableGenerationResult result = TimetableGenerationResult.success(schedule, 1.0);
        assertTrue(result.isComplete(context));
    }

    @Test
    void isCompleteReturnsFalseWhenMissingLessons() {
        SchedulingContext context = SchedulingContext.builder()
                .assignments(List.of(assignment))
                .teachers(List.of(new Teacher(1L, "T001", "John", true)))
                .subjects(List.of(new Subject(1L, "S001", "Math", true, 5, false, false, null)))
                .classStreams(List.of(new ClassStream(1L, "F1E", 1, "East", "Form 1 East")))
                .periodsPerDay(8)
                .build();
        PartialSchedule schedule = new PartialSchedule();
        schedule.place(new PlacedLesson(assignment, new ScheduleSlot(DayOfWeek.MONDAY, 1)));
        TimetableGenerationResult result = TimetableGenerationResult.success(schedule, 0.2);
        assertFalse(result.isComplete(context));
    }

    @Test
    void validateLessonCountsDetectsViolations() {
        SchedulingContext context = SchedulingContext.builder()
                .assignments(List.of(assignment))
                .teachers(List.of(new Teacher(1L, "T001", "John", true)))
                .subjects(List.of(new Subject(1L, "S001", "Math", true, 5, false, false, null)))
                .classStreams(List.of(new ClassStream(1L, "F1E", 1, "East", "Form 1 East")))
                .periodsPerDay(8)
                .build();
        PartialSchedule schedule = new PartialSchedule();
        schedule.place(new PlacedLesson(assignment, new ScheduleSlot(DayOfWeek.MONDAY, 1)));
        TimetableGenerationResult result = TimetableGenerationResult.success(schedule, 0.2);
        List<String> violations = result.validateLessonCounts(context);
        assertFalse(violations.isEmpty());
        assertTrue(violations.getFirst().contains("requires 5 lessons but has 1"));
    }

    @Test
    void requiredPlacementsReturnsCorrectSum() {
        SchedulingContext context = SchedulingContext.builder()
                .assignments(List.of(assignment))
                .teachers(List.of(new Teacher(1L, "T001", "John", true)))
                .subjects(List.of(new Subject(1L, "S001", "Math", true, 5, false, false, null)))
                .classStreams(List.of(new ClassStream(1L, "F1E", 1, "East", "Form 1 East")))
                .periodsPerDay(8)
                .build();
        TimetableGenerationResult result = TimetableGenerationResult.success(new PartialSchedule(), 0.0);
        assertEquals(5, result.requiredPlacements(context));
    }
}
