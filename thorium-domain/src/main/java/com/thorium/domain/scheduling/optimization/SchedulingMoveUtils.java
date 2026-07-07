package com.thorium.domain.scheduling.optimization;

import com.thorium.domain.constraint.SoftConstraintScorer;
import com.thorium.domain.scheduling.PartialSchedule;
import com.thorium.domain.scheduling.PlacedLesson;
import com.thorium.domain.scheduling.SchedulingContext;

import java.util.*;

public final class SchedulingMoveUtils {

    private SchedulingMoveUtils() {}

    public static boolean countsValid(PartialSchedule schedule, SchedulingContext context) {
        Map<Long, Integer> counts = new HashMap<>();
        for (var lesson : schedule.placedLessons()) {
            counts.merge(lesson.assignment().getId(), 1, Integer::sum);
        }
        for (var assignment : context.assignments()) {
            int placed = counts.getOrDefault(assignment.getId(), 0);
            if (placed != assignment.getLessonsPerWeek()) {
                return false;
            }
        }
        return true;
    }

    public static double evaluateMove(SchedulingMove move, PartialSchedule schedule,
                                       SchedulingContext context, SoftConstraintScorer softScorer) {
        PartialSchedule trial = schedule.copy();
        List<PlacedLesson> lessons = new ArrayList<>(trial.placedLessons());
        PlacedLesson l1 = lessons.get(move.lesson1Idx());
        PlacedLesson l2 = lessons.get(move.lesson2Idx());

        trial.remove(l1);
        trial.remove(l2);
        trial.place(new PlacedLesson(l1.assignment(), move.slot2()));
        trial.place(new PlacedLesson(l2.assignment(), move.slot1()));

        return softScorer.score(trial, context);
    }

    public static PartialSchedule applyMove(SchedulingMove move, PartialSchedule schedule) {
        PartialSchedule result = schedule.copy();
        List<PlacedLesson> lessons = new ArrayList<>(result.placedLessons());

        PlacedLesson l1 = lessons.get(move.lesson1Idx());
        PlacedLesson l2 = lessons.get(move.lesson2Idx());

        result.remove(l1);
        result.remove(l2);
        result.place(new PlacedLesson(l1.assignment(), move.slot2()));
        result.place(new PlacedLesson(l2.assignment(), move.slot1()));

        return result;
    }
}
