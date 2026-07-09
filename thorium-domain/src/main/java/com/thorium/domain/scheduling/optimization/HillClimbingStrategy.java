package com.thorium.domain.scheduling.optimization;

import com.thorium.domain.constraint.HardConstraintValidator;
import com.thorium.domain.constraint.SoftConstraintScorer;
import com.thorium.domain.scheduling.PartialSchedule;
import com.thorium.domain.scheduling.SchedulingContext;
import com.thorium.domain.scheduling.TimetableGenerationResult;

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
        return SchedulingMoveUtils.generateSwapNeighbor(schedule, context, hardValidator, 20);
    }

}
