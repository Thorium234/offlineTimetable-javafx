package com.thorium.domain.scheduling;

import com.thorium.domain.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TimetableGeneratorTest {

    private Teacher teacher;
    private Subject subject;
    private ClassStream classStream;
    private TeachingAssignment assignment;

    @BeforeEach
    void setUp() {
        teacher = new Teacher(1L, "T001", "John Doe", 6, 30, true);
        subject = new Subject(1L, "S001", "Geography", true, 5, false, false);
        classStream = new ClassStream(1L, "F1E", 1, "East", "Form 1 East");
        assignment = new TeachingAssignment(1L, 1L, 1L, 1L, 5);
    }

    @Test
    void generatesCompleteTimetableForSimpleCase() {
        SchedulingContext context = SchedulingContext.builder()
                .assignments(List.of(assignment))
                .teachers(List.of(teacher))
                .subjects(List.of(subject))
                .classStreams(List.of(classStream))
                .periodsPerDay(8)
                .build();

        TimetableGenerator generator = new TimetableGenerator();
        TimetableGenerationResult result = generator.generate(context);

        assertTrue(result.isSuccess());
        assertEquals(5, result.schedule().size());
        assertTrue(result.qualityScore() > 0);
    }

    @Test
    void respectsTeacherUnavailability() {
        TeacherAvailability unavailable = new TeacherAvailability(
                1L, 1L, com.thorium.domain.value.DayOfWeek.MONDAY, 1, false);

        SchedulingContext context = SchedulingContext.builder()
                .assignments(List.of(assignment))
                .teachers(List.of(teacher))
                .subjects(List.of(subject))
                .classStreams(List.of(classStream))
                .teacherAvailability(List.of(unavailable))
                .periodsPerDay(8)
                .build();

        TimetableGenerator generator = new TimetableGenerator();
        TimetableGenerationResult result = generator.generate(context);

        assertTrue(result.isSuccess());
        boolean placedOnUnavailable = result.schedule().placedLessons().stream()
                .anyMatch(p -> p.slot().dayOfWeek() == com.thorium.domain.value.DayOfWeek.MONDAY
                        && p.slot().periodNumber() == 1);
        assertFalse(placedOnUnavailable);
    }

    @Test
    void preventsCbcDoubleLessons() {
        SchedulingContext context = SchedulingContext.builder()
                .assignments(List.of(assignment))
                .teachers(List.of(teacher))
                .subjects(List.of(subject))
                .classStreams(List.of(classStream))
                .periodsPerDay(8)
                .cbcNoDoubleLessonEnabled(true)
                .build();

        TimetableGenerator generator = new TimetableGenerator();
        TimetableGenerationResult result = generator.generate(context);

        assertTrue(result.isSuccess());
        for (var day : com.thorium.domain.value.DayOfWeek.workingDays()) {
            List<Integer> periods = result.schedule().placedLessons().stream()
                    .filter(p -> p.slot().dayOfWeek() == day)
                    .map(p -> p.slot().periodNumber())
                    .sorted()
                    .toList();
            for (int i = 1; i < periods.size(); i++) {
                assertNotEquals(1, periods.get(i) - periods.get(i - 1),
                        "Consecutive CBC lessons on " + day);
            }
        }
    }
}
