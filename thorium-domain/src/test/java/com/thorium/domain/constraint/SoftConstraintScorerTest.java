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

class SoftConstraintScorerTest {

    private SoftConstraintScorer scorer;
    private TeachingAssignment assignment;
    private SchedulingContext context;

    @BeforeEach
    void setUp() {
        scorer = new SoftConstraintScorer();
        Subject subject = new Subject(1L, "S001", "Math", true, 5, false, false, null);
        Teacher teacher = new Teacher(1L, "T001", "John", true);
        ClassStream classStream = new ClassStream(1L, "F1E", 1, "East", "Form 1 East");
        assignment = new TeachingAssignment(1L, 1L, 1L, 1L, 5);

        context = SchedulingContext.builder()
                .assignments(List.of(assignment))
                .teachers(List.of(teacher))
                .subjects(List.of(subject))
                .classStreams(List.of(classStream))
                .periodsPerDay(8)
                .build();
    }

    @Test
    void emptyScheduleScoresZero() {
        assertEquals(0.0, scorer.score(new PartialSchedule(), context));
    }

    @Test
    void perfectSpreadAcrossWeekScoresHigh() {
        PartialSchedule schedule = new PartialSchedule();
        schedule.place(new PlacedLesson(assignment, new ScheduleSlot(DayOfWeek.MONDAY, 1)));
        schedule.place(new PlacedLesson(assignment, new ScheduleSlot(DayOfWeek.TUESDAY, 1)));
        schedule.place(new PlacedLesson(assignment, new ScheduleSlot(DayOfWeek.WEDNESDAY, 1)));
        schedule.place(new PlacedLesson(assignment, new ScheduleSlot(DayOfWeek.THURSDAY, 1)));
        schedule.place(new PlacedLesson(assignment, new ScheduleSlot(DayOfWeek.FRIDAY, 1)));

        double score = scorer.score(schedule, context);
        assertTrue(score > 0.9, "Perfect spread should score near 1.0 but was " + score);
    }

    @Test
    void allLessonsSameDayScoresLower() {
        PartialSchedule schedule = new PartialSchedule();
        schedule.place(new PlacedLesson(assignment, new ScheduleSlot(DayOfWeek.MONDAY, 1)));
        schedule.place(new PlacedLesson(assignment, new ScheduleSlot(DayOfWeek.MONDAY, 2)));
        schedule.place(new PlacedLesson(assignment, new ScheduleSlot(DayOfWeek.MONDAY, 3)));
        schedule.place(new PlacedLesson(assignment, new ScheduleSlot(DayOfWeek.MONDAY, 4)));
        schedule.place(new PlacedLesson(assignment, new ScheduleSlot(DayOfWeek.MONDAY, 5)));

        double score = scorer.score(schedule, context);
        double perfectScore = scorer.score(perfectSpread(), context);
        assertTrue(score < perfectScore, "All-in-one-day should score lower than perfect spread");
    }

    @Test
    void consecutiveLessonsPenaltyApplied() {
        PartialSchedule consecutive = new PartialSchedule();
        consecutive.place(new PlacedLesson(assignment, new ScheduleSlot(DayOfWeek.MONDAY, 1)));
        consecutive.place(new PlacedLesson(assignment, new ScheduleSlot(DayOfWeek.MONDAY, 2)));
        consecutive.place(new PlacedLesson(assignment, new ScheduleSlot(DayOfWeek.TUESDAY, 1)));
        consecutive.place(new PlacedLesson(assignment, new ScheduleSlot(DayOfWeek.WEDNESDAY, 1)));
        consecutive.place(new PlacedLesson(assignment, new ScheduleSlot(DayOfWeek.THURSDAY, 1)));

        PartialSchedule spread = new PartialSchedule();
        spread.place(new PlacedLesson(assignment, new ScheduleSlot(DayOfWeek.MONDAY, 1)));
        spread.place(new PlacedLesson(assignment, new ScheduleSlot(DayOfWeek.MONDAY, 3)));
        spread.place(new PlacedLesson(assignment, new ScheduleSlot(DayOfWeek.TUESDAY, 1)));
        spread.place(new PlacedLesson(assignment, new ScheduleSlot(DayOfWeek.WEDNESDAY, 1)));
        spread.place(new PlacedLesson(assignment, new ScheduleSlot(DayOfWeek.THURSDAY, 1)));

        double consecutiveScore = scorer.score(consecutive, context);
        double spreadScore = scorer.score(spread, context);
        assertTrue(consecutiveScore < spreadScore,
                "Consecutive lessons should score lower than spread-out ones");
    }

    @Test
    void doubleSubjectNotPenalizedForConsecutive() {
        Subject doubleSubject = new Subject(2L, "S002", "Physics", true, 5, true, true, null);
        TeachingAssignment doubleAssignment = new TeachingAssignment(2L, 1L, 2L, 1L, 4, LessonDuration.DOUBLE);

        SchedulingContext ctx = SchedulingContext.builder()
                .assignments(List.of(doubleAssignment))
                .teachers(List.of(new Teacher(1L, "T001", "John", true)))
                .subjects(List.of(doubleSubject))
                .classStreams(List.of(new ClassStream(1L, "F1E", 1, "East", "Form 1 East")))
                .periodsPerDay(8)
                .build();

        PartialSchedule schedule = new PartialSchedule();
        schedule.place(new PlacedLesson(doubleAssignment, new ScheduleSlot(DayOfWeek.MONDAY, 1)));
        schedule.place(new PlacedLesson(doubleAssignment, new ScheduleSlot(DayOfWeek.MONDAY, 2)));
        schedule.place(new PlacedLesson(doubleAssignment, new ScheduleSlot(DayOfWeek.WEDNESDAY, 1)));
        schedule.place(new PlacedLesson(doubleAssignment, new ScheduleSlot(DayOfWeek.WEDNESDAY, 2)));

        double score = scorer.score(schedule, ctx);
        assertTrue(score > 0.7, "Double-subject consecutive should not be penalized heavily");
    }

    @Test
    void scorePlacementEvaluatesTrial() {
        PartialSchedule schedule = new PartialSchedule();
        schedule.place(new PlacedLesson(assignment, new ScheduleSlot(DayOfWeek.MONDAY, 1)));
        schedule.place(new PlacedLesson(assignment, new ScheduleSlot(DayOfWeek.TUESDAY, 1)));
        schedule.place(new PlacedLesson(assignment, new ScheduleSlot(DayOfWeek.WEDNESDAY, 1)));
        schedule.place(new PlacedLesson(assignment, new ScheduleSlot(DayOfWeek.THURSDAY, 1)));

        double trialScore = scorer.scorePlacement(assignment,
                new ScheduleSlot(DayOfWeek.FRIDAY, 1), schedule, context);
        double badTrialScore = scorer.scorePlacement(assignment,
                new ScheduleSlot(DayOfWeek.THURSDAY, 2), schedule, context);

        assertTrue(trialScore > badTrialScore,
                "Spreading to a new day should score better than stacking");
    }

    private PartialSchedule perfectSpread() {
        PartialSchedule s = new PartialSchedule();
        s.place(new PlacedLesson(assignment, new ScheduleSlot(DayOfWeek.MONDAY, 1)));
        s.place(new PlacedLesson(assignment, new ScheduleSlot(DayOfWeek.TUESDAY, 1)));
        s.place(new PlacedLesson(assignment, new ScheduleSlot(DayOfWeek.WEDNESDAY, 1)));
        s.place(new PlacedLesson(assignment, new ScheduleSlot(DayOfWeek.THURSDAY, 1)));
        s.place(new PlacedLesson(assignment, new ScheduleSlot(DayOfWeek.FRIDAY, 1)));
        return s;
    }
}
