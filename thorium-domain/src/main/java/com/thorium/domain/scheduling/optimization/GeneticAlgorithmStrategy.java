package com.thorium.domain.scheduling.optimization;

import com.thorium.domain.scheduling.SchedulingContext;
import com.thorium.domain.scheduling.TimetableGenerationResult;

public class GeneticAlgorithmStrategy implements OptimizationStrategy {

    private final HillClimbingStrategy fallback = new HillClimbingStrategy();

    @Override
    public TimetableGenerationResult optimize(TimetableGenerationResult initial, SchedulingContext context) {
        return fallback.optimize(initial, context);
    }
}
