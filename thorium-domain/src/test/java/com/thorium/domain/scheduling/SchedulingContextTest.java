package com.thorium.domain.scheduling;

import com.thorium.domain.model.*;
import com.thorium.domain.value.DayOfWeek;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SchedulingContextTest {

    @Test
    void buildWithValidInputsSucceeds() {
        SchedulingContext context = SchedulingContext.builder()
                .assignments(List.of(new TeachingAssignment(1L, 1L, 1L, 1L, 2)))
                .teachers(List.of(new Teacher(1L, "T001", "John", true)))
                .subjects(List.of(new Subject(1L, "S001", "Math", true, 5, false, false, null)))
                .classStreams(List.of(new ClassStream(1L, "F1E", 1, "East", "Form 1 East")))
                .periodsPerDay(8)
                .build();
        assertNotNull(context);
        assertEquals(8, context.periodsPerDay());
        assertEquals(5, context.workingDays().size());
    }

    @Test
    void buildWithNoAssignmentsThrows() {
        assertThrows(IllegalStateException.class, () ->
                SchedulingContext.builder()
                        .periodsPerDay(8)
                        .build());
    }

    @Test
    void buildWithNonPositivePeriodsThrows() {
        assertThrows(IllegalStateException.class, () ->
                SchedulingContext.builder()
                        .assignments(List.of(new TeachingAssignment(1L, 1L, 1L, 1L, 2)))
                        .periodsPerDay(0)
                        .build());
    }

    @Test
    void buildWithNegativePeriodsThrows() {
        assertThrows(IllegalStateException.class, () ->
                SchedulingContext.builder()
                        .assignments(List.of(new TeachingAssignment(1L, 1L, 1L, 1L, 2)))
                        .periodsPerDay(-1)
                        .build());
    }

    @Test
    void isTeacherUnavailableReturnsCorrectly() {
        TeacherAvailability unavailable = new TeacherAvailability(
                1L, 1L, DayOfWeek.MONDAY, 1, false);
        SchedulingContext context = SchedulingContext.builder()
                .assignments(List.of(new TeachingAssignment(1L, 1L, 1L, 1L, 2)))
                .teachers(List.of(new Teacher(1L, "T001", "John", true)))
                .subjects(List.of(new Subject(1L, "S001", "Math", true, 5, false, false, null)))
                .classStreams(List.of(new ClassStream(1L, "F1E", 1, "East", "Form 1 East")))
                .teacherAvailability(List.of(unavailable))
                .periodsPerDay(8)
                .build();
        assertTrue(context.isTeacherUnavailable(1L, new ScheduleSlot(DayOfWeek.MONDAY, 1)));
        assertFalse(context.isTeacherUnavailable(1L, new ScheduleSlot(DayOfWeek.MONDAY, 2)));
        assertFalse(context.isTeacherUnavailable(999L, new ScheduleSlot(DayOfWeek.MONDAY, 1)));
    }

    @Test
    void allSlotsReturnsCorrectCount() {
        SchedulingContext context = SchedulingContext.builder()
                .assignments(List.of(new TeachingAssignment(1L, 1L, 1L, 1L, 2)))
                .teachers(List.of(new Teacher(1L, "T001", "John", true)))
                .subjects(List.of(new Subject(1L, "S001", "Math", true, 5, false, false, null)))
                .classStreams(List.of(new ClassStream(1L, "F1E", 1, "East", "Form 1 East")))
                .periodsPerDay(8)
                .build();
        assertEquals(40, context.allSlots().size()); // 5 days * 8 periods
    }

    @Test
    void totalSlotsMatchesWorkingDaysTimesPeriods() {
        SchedulingContext context = SchedulingContext.builder()
                .assignments(List.of(new TeachingAssignment(1L, 1L, 1L, 1L, 2)))
                .teachers(List.of(new Teacher(1L, "T001", "John", true)))
                .subjects(List.of(new Subject(1L, "S001", "Math", true, 5, false, false, null)))
                .classStreams(List.of(new ClassStream(1L, "F1E", 1, "East", "Form 1 East")))
                .periodsPerDay(6)
                .build();
        assertEquals(30, context.totalSlots());
    }

    @Test
    void teacherSubjectAndClassStreamLookupsWork() {
        Teacher teacher = new Teacher(1L, "T001", "John", true);
        Subject subject = new Subject(1L, "S001", "Math", true, 5, false, false, null);
        ClassStream cs = new ClassStream(1L, "F1E", 1, "East", "Form 1 East");
        SchedulingContext context = SchedulingContext.builder()
                .assignments(List.of(new TeachingAssignment(1L, 1L, 1L, 1L, 2)))
                .teachers(List.of(teacher))
                .subjects(List.of(subject))
                .classStreams(List.of(cs))
                .periodsPerDay(8)
                .build();
        assertEquals(teacher, context.teacher(1L).orElseThrow());
        assertEquals(subject, context.subject(1L).orElseThrow());
        assertEquals(cs, context.classStream(1L).orElseThrow());
        assertTrue(context.teacher(999L).isEmpty());
        assertTrue(context.subject(999L).isEmpty());
        assertTrue(context.classStream(999L).isEmpty());
    }

    @Test
    void constraintsEnableCbcNoDouble() {
        SchedulingContext context = SchedulingContext.builder()
                .assignments(List.of(new TeachingAssignment(1L, 1L, 1L, 1L, 2)))
                .teachers(List.of(new Teacher(1L, "T001", "John", true)))
                .subjects(List.of(new Subject(1L, "S001", "Math", true, 5, false, false, null)))
                .classStreams(List.of(new ClassStream(1L, "F1E", 1, "East", "Form 1 East")))
                .periodsPerDay(8)
                .build();
        assertFalse(context.isCbcNoDoubleLessonEnabled()); // default is false in this test
    }

    @Test
    void builderDefaultsToWorkingDays() {
        SchedulingContext context = SchedulingContext.builder()
                .assignments(List.of(new TeachingAssignment(1L, 1L, 1L, 1L, 2)))
                .teachers(List.of(new Teacher(1L, "T001", "John", true)))
                .subjects(List.of(new Subject(1L, "S001", "Math", true, 5, false, false, null)))
                .classStreams(List.of(new ClassStream(1L, "F1E", 1, "East", "Form 1 East")))
                .periodsPerDay(8)
                .build();
        assertEquals(DayOfWeek.workingDays(), context.workingDays());
    }

    @Test
    void customWorkingDaysAreRespected() {
        SchedulingContext context = SchedulingContext.builder()
                .assignments(List.of(new TeachingAssignment(1L, 1L, 1L, 1L, 2)))
                .teachers(List.of(new Teacher(1L, "T001", "John", true)))
                .subjects(List.of(new Subject(1L, "S001", "Math", true, 5, false, false, null)))
                .classStreams(List.of(new ClassStream(1L, "F1E", 1, "East", "Form 1 East")))
                .workingDays(List.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY))
                .periodsPerDay(8)
                .build();
        assertEquals(3, context.workingDays().size());
        assertEquals(24, context.allSlots().size());
    }
}
