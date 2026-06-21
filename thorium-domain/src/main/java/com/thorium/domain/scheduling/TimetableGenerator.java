package com.thorium.domain.scheduling;

import com.thorium.domain.constraint.HardConstraintValidator;
import com.thorium.domain.constraint.SoftConstraintScorer;
import com.thorium.domain.scheduling.optimization.HillClimbingStrategy;
import com.thorium.domain.scheduling.optimization.OptimizationStrategy;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class TimetableGenerator {

    private final GreedyScheduler greedyScheduler;
    private final BacktrackingScheduler backtrackingScheduler;
    private final HardConstraintValidator hardValidator;
    private final SoftConstraintScorer softScorer;
    private final Optional<OptimizationStrategy> optimizationStrategy;

    public TimetableGenerator() {
        this(new HillClimbingStrategy());
    }

    public TimetableGenerator(OptimizationStrategy optimizationStrategy) {
        this.hardValidator = new HardConstraintValidator();
        this.softScorer = new SoftConstraintScorer();
        this.greedyScheduler = new GreedyScheduler(hardValidator, softScorer);
        this.backtrackingScheduler = new BacktrackingScheduler(hardValidator, softScorer);
        this.optimizationStrategy = Optional.ofNullable(optimizationStrategy);
    }

    public TimetableGenerationResult generate(SchedulingContext context) {
        PartialSchedule greedyResult = greedyScheduler.schedule(context);
        int required = context.assignments().stream()
                .mapToInt(a -> a.getLessonsPerWeek()).sum();

        TimetableGenerationResult result;
        if (greedyResult.size() >= required) {
            HardConstraintValidator.ValidationResult validation =
                    hardValidator.validateComplete(greedyResult, context);
            if (validation.isValid()) {
                double quality = softScorer.score(greedyResult, context);
                result = TimetableGenerationResult.success(greedyResult, quality);
            } else {
                result = backtrackingScheduler.resolve(context, new PartialSchedule());
            }
        } else {
            result = backtrackingScheduler.resolve(context, greedyResult);
        }

        if (result.isSuccess() && optimizationStrategy.isPresent()) {
            return optimizationStrategy.get().optimize(result, context);
        }
        return result;
    }

    public List<String> preflightChecks(SchedulingContext context) {
        List<String> issues = new ArrayList<>();
        int totalRequired = context.assignments().stream()
                .mapToInt(a -> a.getLessonsPerWeek()).sum();
        long classCount = countDistinctClasses(context);
        if (totalRequired > (long) context.totalSlots() * classCount) {
            issues.add("Total required lessons (" + totalRequired
                    + ") may exceed available class slots");
        }
        return issues;
    }

    private long countDistinctClasses(SchedulingContext context) {
        return context.assignments().stream()
                .map(a -> a.getClassStreamId())
                .distinct()
                .count();
    }
}
