package com.thorium.domain.scheduling.optimization;

import com.thorium.domain.constraint.HardConstraintValidator;
import com.thorium.domain.constraint.SoftConstraintScorer;
import com.thorium.domain.scheduling.PartialSchedule;
import com.thorium.domain.scheduling.SchedulingContext;
import com.thorium.domain.scheduling.TimetableGenerationResult;

import java.util.Random;

public class SimulatedAnnealingStrategy implements OptimizationStrategy {

    private static final int MAX_ITERATIONS = 10000;
    private static final double INITIAL_TEMPERATURE = 1.0;
    private static final double COOLING_RATE = 0.995;

    private final HardConstraintValidator hardValidator;
    private final SoftConstraintScorer softScorer;
    private final Random random;

    public SimulatedAnnealingStrategy() {
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
        double temperature = INITIAL_TEMPERATURE;

        for (int iteration = 0; iteration < MAX_ITERATIONS; iteration++) {
            PartialSchedule neighbor = generateNeighbor(current, context);
            if (neighbor == null) {
                temperature *= COOLING_RATE;
                continue;
            }

            double neighborScore = softScorer.score(neighbor, context);
            double delta = neighborScore - currentScore;

            if (delta > 0 || random.nextDouble() < Math.exp(delta / temperature)) {
                current = neighbor;
                currentScore = neighborScore;
            }

            temperature *= COOLING_RATE;
        }

        return TimetableGenerationResult.success(current, currentScore);
    }

    private PartialSchedule generateNeighbor(PartialSchedule schedule, SchedulingContext context) {
        return SchedulingMoveUtils.generateSwapNeighbor(schedule, context, hardValidator, 20);
    }
}
