package com.thorium.domain.constraint;

import com.thorium.domain.model.*;
import com.thorium.domain.scheduling.PartialSchedule;
import com.thorium.domain.scheduling.PlacedLesson;
import com.thorium.domain.scheduling.SchedulingContext;
import com.thorium.domain.value.DayOfWeek;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HardConstraintValidatorTest {

    private HardConstraintValidator validator;
    private TeachingAssignment assignment;
    private SchedulingContext context;

    @BeforeEach
    void setUp() {
        validator = new HardConstraintValidator();
        Teacher teacher = new Teacher(1L, "T001", "John", 6, 30, true);
        Subject subject = new Subject(1L, "S001", "Geography", true, 5, false, false);
        ClassStream classStream = new ClassStream(1L, "F1E", 1, "East", "Form 1 East");
        assignment = new TeachingAssignment(1L, 1L, 1L, 1L, 2);

        context = SchedulingContext.builder()
                .assignments(List.of(assignment))
                .teachers(List.of(teacher))
                .subjects(List.of(subject))
                .classStreams(List.of(classStream))
                .periodsPerDay(8)
                .cbcNoDoubleLessonEnabled(true)
                .build();
    }

    @Test
    void allowsPlacementOnEmptySchedule() {
        ScheduleSlot slot = new ScheduleSlot(DayOfWeek.MONDAY, 1);
        assertTrue(validator.canPlace(assignment, slot, new PartialSchedule(), context));
    }

    @Test
    void rejectsTeacherClash() {
        ScheduleSlot slot = new ScheduleSlot(DayOfWeek.MONDAY, 1);
        PartialSchedule schedule = new PartialSchedule();
        schedule.place(new PlacedLesson(assignment, slot));

        TeachingAssignment otherClass = new TeachingAssignment(2L, 1L, 1L, 2L, 2);
        assertFalse(validator.canPlace(otherClass, slot, schedule, context));
    }

    @Test
    void rejectsClassClash() {
        ScheduleSlot slot = new ScheduleSlot(DayOfWeek.TUESDAY, 3);
        PartialSchedule schedule = new PartialSchedule();
        schedule.place(new PlacedLesson(assignment, slot));

        TeachingAssignment sameClass = new TeachingAssignment(2L, 2L, 2L, 1L, 2);
        assertFalse(validator.canPlace(sameClass, slot, schedule, context));
    }

    @Test
    void rejectsUnavailableTeacherSlot() {
        TeacherAvailability unavailable = new TeacherAvailability(
                1L, 1L, DayOfWeek.FRIDAY, 4, false);
        SchedulingContext ctx = SchedulingContext.builder()
                .assignments(List.of(assignment))
                .teachers(List.of(new Teacher(1L, "T001", "John", 6, 30, true)))
                .subjects(List.of(new Subject(1L, "S001", "Geography", true, 5, false, false)))
                .classStreams(List.of(new ClassStream(1L, "F1E", 1, "East", "Form 1 East")))
                .teacherAvailability(List.of(unavailable))
                .periodsPerDay(8)
                .build();

        ScheduleSlot slot = new ScheduleSlot(DayOfWeek.FRIDAY, 4);
        assertFalse(validator.canPlace(assignment, slot, new PartialSchedule(), ctx));
    }

    @Test
    void rejectsCbcConsecutiveDoubleLesson() {
        PartialSchedule schedule = new PartialSchedule();
        schedule.place(new PlacedLesson(assignment, new ScheduleSlot(DayOfWeek.WEDNESDAY, 2)));

        assertFalse(validator.canPlace(
                assignment, new ScheduleSlot(DayOfWeek.WEDNESDAY, 3), schedule, context));
    }

    @Test
    void validateCompleteReportsMissingLessons() {
        PartialSchedule schedule = new PartialSchedule();
        schedule.place(new PlacedLesson(assignment, new ScheduleSlot(DayOfWeek.MONDAY, 1)));

        HardConstraintValidator.ValidationResult result = validator.validateComplete(schedule, context);
        assertFalse(result.isValid());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("expected 2")));
    }
}
