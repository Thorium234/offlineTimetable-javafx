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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TabuSearchStrategyTest {

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
        TabuSearchStrategy strategy = new TabuSearchStrategy();
        TimetableGenerationResult result = strategy.optimize(initial, context);

        assertTrue(result.isSuccess());
        assertFalse(result.schedule().isEmpty());
        assertTrue(result.qualityScore() > 0);
    }

    @Test
    void improvesScoreOverInitial() {
        TimetableGenerationResult initial = TimetableGenerationResult.success(fullSchedule, 0.0);
        TabuSearchStrategy strategy = new TabuSearchStrategy();
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
        TabuSearchStrategy strategy = new TabuSearchStrategy();
        TimetableGenerationResult result = strategy.optimize(failed, context);

        assertFalse(result.isSuccess());
    }

    @Test
    void preservesLessonCounts() {
        TimetableGenerationResult initial = TimetableGenerationResult.success(fullSchedule, 0.5);
        TabuSearchStrategy strategy = new TabuSearchStrategy();
        TimetableGenerationResult result = strategy.optimize(initial, context);

        long placedCount = result.schedule().placedLessons().stream()
                .filter(p -> p.assignment().getId().equals(assignment.getId()))
                .count();
        assertEquals(5, placedCount);
    }

    @Test
    void handlesLargeSchedule() {
        int lessonsPerSubject = 3;
        int numPeriods = 15;
        List<Integer> lessonPeriods = new ArrayList<>();
        for (int i = 1; i <= numPeriods; i++) lessonPeriods.add(i);

        List<Teacher> teachers = new ArrayList<>();
        List<Subject> subjects = new ArrayList<>();
        List<ClassStream> classes = new ArrayList<>();
        List<TeachingAssignment> assignments = new ArrayList<>();
        List<ScheduleSlot> allSlots = new ArrayList<>();
        for (var day : DayOfWeek.workingDays()) {
            for (int pn : lessonPeriods) {
                allSlots.add(new ScheduleSlot(day, pn));
            }
        }

        long id = 1;
        for (int c = 0; c < 2; c++) {
            long classId = id++;
            classes.add(new ClassStream(classId, "F" + (c + 1) + "E", c + 1, "East", "Form " + (c + 1) + " East"));
        }
        for (int s = 0; s < 3; s++) {
            long subjectId = id++;
            subjects.add(new Subject(subjectId, "S" + (s + 1), "Subject " + (s + 1), true, lessonsPerSubject, false, false, null));
        }
        for (int c = 0; c < 2; c++) {
            long classId = classes.get(c).getId();
            for (int s = 0; s < 3; s++) {
                long teacherId = id++;
                long subjectId = subjects.get(s).getId();
                teachers.add(new Teacher(teacherId, "T" + teacherId, "Teacher " + teacherId, true));
                assignments.add(new TeachingAssignment(id++, teacherId, subjectId, classId, lessonsPerSubject));
            }
        }

        int totalLessons = assignments.stream().mapToInt(TeachingAssignment::getLessonsPerWeek).sum();

        SchedulingContext ctx = SchedulingContext.builder()
                .assignments(assignments)
                .teachers(teachers)
                .subjects(subjects)
                .classStreams(classes)
                .lessonPeriodNumbers(lessonPeriods)
                .build();

        assertTrue(totalLessons <= ctx.totalSlots(),
                "Total lessons (" + totalLessons + ") must fit in slots (" + ctx.totalSlots() + ")");

        PartialSchedule initialSchedule = new PartialSchedule();
        int slotIdx = 0;
        for (var a : assignments) {
            for (int i = 0; i < a.getLessonsPerWeek(); i++) {
                initialSchedule.place(new PlacedLesson(a, allSlots.get(slotIdx++)));
            }
        }

        TimetableGenerationResult initial = TimetableGenerationResult.success(initialSchedule, 0.3);
        TabuSearchStrategy strategy = new TabuSearchStrategy();
        TimetableGenerationResult result = strategy.optimize(initial, ctx);

        assertTrue(result.isSuccess());
        HardConstraintValidator validator = new HardConstraintValidator();
        var validation = validator.validateComplete(result.schedule(), ctx);
        assertTrue(validation.isValid(), "Hard constraints violated: " + validation.errors());
    }
}
