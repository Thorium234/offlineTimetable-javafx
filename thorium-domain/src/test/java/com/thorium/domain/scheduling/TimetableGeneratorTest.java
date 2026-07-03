package com.thorium.domain.scheduling;

import com.thorium.domain.constraint.HardConstraintValidator;
import com.thorium.domain.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TimetableGeneratorTest {

    private Teacher teacher;
    private Subject subject;
    private ClassStream classStream;
    private TeachingAssignment assignment;

    @BeforeEach
    void setUp() {
        teacher = new Teacher(1L, "T001", "John Doe", true);
        subject = new Subject(1L, "S001", "Geography", true, 5, false, false, null);
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
    void generatesCompleteTimetableForRealWorldCapacity() {
        int numClasses = 5;
        int numSubjects = 10;
        int lessonsPerSubject = 5;
        List<Integer> lessonPeriods = List.of(2, 3, 4, 6, 7, 9, 10, 12, 13, 14);

        List<ClassStream> classes = new ArrayList<>();
        List<Subject> subjects = new ArrayList<>();
        List<Teacher> teachers = new ArrayList<>();
        List<TeachingAssignment> assignments = new ArrayList<>();

        long id = 1;
        for (int c = 0; c < numClasses; c++) {
            long classId = id++;
            classes.add(new ClassStream(classId, "F" + (c + 1) + "E", c + 1, "East", "Form " + (c + 1) + " East"));
        }
        for (int s = 0; s < numSubjects; s++) {
            long subjectId = id++;
            subjects.add(new Subject(subjectId, "S" + (s + 1), "Subject " + (s + 1), true, 5, false, false, null));
        }
        long assignmentId = 1;
        for (int c = 0; c < numClasses; c++) {
            long classId = classes.get(c).getId();
            for (int s = 0; s < numSubjects; s++) {
                long teacherId = id++;
                long subjectId = subjects.get(s).getId();
                teachers.add(new Teacher(teacherId, "T" + teacherId, "Teacher " + teacherId, true, 10, 50));
                assignments.add(new TeachingAssignment(assignmentId++, teacherId, subjectId, classId, lessonsPerSubject));
            }
        }

        SchedulingContext context = SchedulingContext.builder()
                .assignments(assignments)
                .teachers(teachers)
                .subjects(subjects)
                .classStreams(classes)
                .lessonPeriodNumbers(lessonPeriods)
                .build();

        assertEquals(50, context.totalSlots());
        assertEquals(250, context.assignments().stream().mapToInt(a -> a.getLessonsPerWeek()).sum());

        List<String> preflight = new TimetableGenerator().preflightChecks(context);
        assertTrue(preflight.isEmpty(), "Preflight checks should pass: " + preflight);

        TimetableGenerator generator = new TimetableGenerator();
        TimetableGenerationResult result = generator.generate(context);

        assertTrue(result.isSuccess(), "Generation should succeed: " + result.errors());
        assertEquals(250, result.schedule().size());

        HardConstraintValidator validator = new HardConstraintValidator();
        var validation = validator.validateComplete(result.schedule(), context);
        assertTrue(validation.isValid(), "Hard constraints violated: " + validation.errors());
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
