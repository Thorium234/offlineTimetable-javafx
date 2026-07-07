package com.thorium.domain.scheduling.optimization;

import com.thorium.domain.constraint.HardConstraintValidator;
import com.thorium.domain.constraint.SoftConstraintScorer;
import com.thorium.domain.model.ScheduleSlot;
import com.thorium.domain.model.TeachingAssignment;
import com.thorium.domain.scheduling.PartialSchedule;
import com.thorium.domain.scheduling.PlacedLesson;
import com.thorium.domain.scheduling.SchedulingContext;
import com.thorium.domain.scheduling.TimetableGenerationResult;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class HillClimbingStrategy implements OptimizationStrategy {

    private static final int MAX_ITERATIONS = 5000;
    private static final int STAGNATION_LIMIT = 200;

    private final HardConstraintValidator hardValidator;
    private final SoftConstraintScorer softScorer;
    private final Random random;

    public HillClimbingStrategy() {
        this.hardValidator = new HardConstraintValidator();
        this.softScorer = new SoftConstraintScorer();
        this.random = new Random();
    }

    @Override
    public TimetableGenerationResult optimize(TimetableGenerationResult initial, SchedulingContext context) {
        if (!initial.isSuccess()) {
            return initial;
        }

        PartialSchedule current = initial.schedule().copy();
        double currentScore = softScorer.score(current, context);
        int stagnationCount = 0;

        for (int iteration = 0; iteration < MAX_ITERATIONS; iteration++) {
            PartialSchedule neighbor = generateNeighbor(current, context);
            if (neighbor == null) {
                stagnationCount++;
                if (stagnationCount > STAGNATION_LIMIT) break;
                continue;
            }

            double neighborScore = softScorer.score(neighbor, context);
            if (neighborScore > currentScore) {
                current = neighbor;
                currentScore = neighborScore;
                stagnationCount = 0;
            } else {
                stagnationCount++;
                if (stagnationCount > STAGNATION_LIMIT) break;
            }
        }

        return TimetableGenerationResult.success(current, currentScore);
    }

    private PartialSchedule generateNeighbor(PartialSchedule schedule, SchedulingContext context) {
        List<PlacedLesson> lessons = new ArrayList<>(schedule.placedLessons());
        if (lessons.size() < 2) return null;

        for (int attempt = 0; attempt < 20; attempt++) {
            int idx1 = random.nextInt(lessons.size());
            int idx2 = random.nextInt(lessons.size());
            if (idx1 == idx2) continue;

            PlacedLesson lesson1 = lessons.get(idx1);
            PlacedLesson lesson2 = lessons.get(idx2);
            ScheduleSlot slot1 = lesson1.slot();
            ScheduleSlot slot2 = lesson2.slot();

            PartialSchedule trial = schedule.copy();
            trial.remove(lesson1);
            trial.remove(lesson2);

            PlacedLesson swapped1 = new PlacedLesson(lesson1.assignment(), slot2);
            PlacedLesson swapped2 = new PlacedLesson(lesson2.assignment(), slot1);

            if (!hardValidator.canPlace(swapped1.assignment(), swapped1.slot(), trial, context)
                    || !hardValidator.canPlace(swapped2.assignment(), swapped2.slot(), trial, context)) {
                continue;
            }
            trial.place(swapped1);
            trial.place(swapped2);
            if (SchedulingMoveUtils.countsValid(trial, context)) {
                return trial;
            }
        }
        return null;
    }

}
