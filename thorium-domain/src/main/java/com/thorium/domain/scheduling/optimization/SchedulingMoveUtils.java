package com.thorium.domain.scheduling.optimization;

import com.thorium.domain.constraint.HardConstraintValidator;
import com.thorium.domain.constraint.SoftConstraintScorer;
import com.thorium.domain.model.ScheduleSlot;
import com.thorium.domain.scheduling.PartialSchedule;
import com.thorium.domain.scheduling.PlacedLesson;
import com.thorium.domain.scheduling.SchedulingContext;

import java.util.*;

public final class SchedulingMoveUtils {

    private SchedulingMoveUtils() {}

    public static PartialSchedule generateSwapNeighbor(PartialSchedule schedule, SchedulingContext context,
                                                        HardConstraintValidator hardValidator, int maxAttempts) {
        List<PlacedLesson> lessons = new ArrayList<>(schedule.placedLessons());
        if (lessons.size() < 2) return null;
        Random random = new Random();

        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            int idx1 = random.nextInt(lessons.size());
            int idx2 = random.nextInt(lessons.size());
            if (idx1 == idx2) continue;

            PartialSchedule result = trySwap(schedule, idx1, idx2, context, hardValidator);
            if (result != null) return result;
        }
        return null;
    }

    public static List<SchedulingMove> generateSwapCandidates(PartialSchedule schedule, SchedulingContext context,
                                                                HardConstraintValidator hardValidator,
                                                                int maxCandidates) {
        List<PlacedLesson> lessons = new ArrayList<>(schedule.placedLessons());
        if (lessons.size() < 2) return List.of();
        Random random = new Random();

        Set<String> seen = new HashSet<>();
        List<SchedulingMove> candidates = new ArrayList<>();
        int attempts = 0;

        while (candidates.size() < maxCandidates && attempts < maxCandidates * 5) {
            attempts++;
            int idx1 = random.nextInt(lessons.size());
            int idx2 = random.nextInt(lessons.size());
            if (idx1 == idx2) continue;

            String key = Math.min(idx1, idx2) + "-" + Math.max(idx1, idx2);
            if (!seen.add(key)) continue;

            PartialSchedule trial = trySwap(schedule, idx1, idx2, context, hardValidator);
            if (trial == null) continue;
            if (!countsValid(trial, context)) continue;

            PlacedLesson l1 = lessons.get(idx1);
            PlacedLesson l2 = lessons.get(idx2);
            candidates.add(new SchedulingMove(idx1, idx2, l2.slot(), l1.slot()));
        }

        return candidates;
    }

    private static PartialSchedule trySwap(PartialSchedule schedule, int idx1, int idx2,
                                            SchedulingContext context, HardConstraintValidator hardValidator) {
        List<PlacedLesson> lessons = new ArrayList<>(schedule.placedLessons());
        PlacedLesson l1 = lessons.get(idx1);
        PlacedLesson l2 = lessons.get(idx2);
        ScheduleSlot s1 = l1.slot();
        ScheduleSlot s2 = l2.slot();

        PartialSchedule trial = schedule.copy();
        trial.remove(l1);
        trial.remove(l2);

        PlacedLesson swapped1 = new PlacedLesson(l1.assignment(), s2);
        PlacedLesson swapped2 = new PlacedLesson(l2.assignment(), s1);

        if (!hardValidator.canPlace(swapped1.assignment(), swapped1.slot(), trial, context)
                || !hardValidator.canPlace(swapped2.assignment(), swapped2.slot(), trial, context)) {
            return null;
        }
        trial.place(swapped1);
        trial.place(swapped2);
        return trial;
    }

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
