package com.thorium.domain.scheduling;

import com.thorium.domain.constraint.HardConstraintValidator;
import com.thorium.domain.constraint.SoftConstraintScorer;
import com.thorium.domain.model.ClassStream;
import com.thorium.domain.model.Teacher;
import com.thorium.domain.scheduling.optimization.HybridMetaheuristicStrategy;
import com.thorium.domain.scheduling.optimization.OptimizationStrategy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

public class TimetableGenerator {

    private static final Logger LOG = Logger.getLogger(TimetableGenerator.class.getName());

    private final HardConstraintValidator hardValidator;
    private final Optional<OptimizationStrategy> optimizationStrategy;

    public TimetableGenerator() {
        this(new HybridMetaheuristicStrategy());
    }

    public TimetableGenerator(OptimizationStrategy optimizationStrategy) {
        this.hardValidator = new HardConstraintValidator();
        this.optimizationStrategy = Optional.ofNullable(optimizationStrategy);
    }

    public TimetableGenerationResult generate(SchedulingContext context) {
        return generate(context, null);
    }

    public TimetableGenerationResult generate(SchedulingContext context, GenerationProgressCallback callback) {
        if (callback != null) callback.log("INFO", "Starting generation with "
                + context.assignments().size() + " assignments");
        LOG.info("Starting timetable generation with " + context.assignments().size() + " assignments");
        long start = System.currentTimeMillis();

        SoftConstraintScorer softScorer = context.softConstraintScorer();
        GreedyScheduler greedyScheduler = new GreedyScheduler(hardValidator, softScorer);
        BacktrackingScheduler backtrackingScheduler = new BacktrackingScheduler(hardValidator, softScorer);

        int required = context.assignments().stream()
                .mapToInt(a -> a.getLessonsPerWeek()).sum();

        if (callback != null && callback.isCancelled()) {
            callback.log("INFO", "Generation cancelled before greedy phase");
            return TimetableGenerationResult.failure(List.of("Cancelled"));
        }

        if (callback != null) callback.tierChange("GREEDY");
        PartialSchedule greedyResult = greedyScheduler.schedule(context, callback);
        LOG.fine("Greedy scheduler placed " + greedyResult.size() + "/" + required + " lessons");
        if (callback != null) callback.log("INFO", "Greedy scheduler placed "
                + greedyResult.size() + "/" + required + " lessons");

        HardConstraintValidator.ValidationResult greedyValidation = hardValidator.validateComplete(greedyResult, context);
        if (!greedyValidation.isValid() && callback != null) {
            for (String err : greedyValidation.errors()) {
                callback.log("WARN", "Greedy validation: " + err);
            }
        }

        TimetableGenerationResult result;
        if (greedyResult.size() >= required && greedyValidation.isValid()) {
            double quality = softScorer.score(greedyResult, context);
            result = TimetableGenerationResult.success(greedyResult, quality);
            if (callback != null) callback.log("INFO", "Greedy schedule succeeded (quality="
                    + String.format("%.3f", quality) + ")");
            LOG.info("Greedy schedule succeeded (quality=" + String.format("%.3f", quality) + ")");
        } else {
            if (greedyResult.size() >= required && !greedyValidation.isValid()) {
                if (callback != null) callback.log("WARN", "Greedy validation failed, trying backtracking from scratch");
                LOG.fine("Greedy validation failed, falling back to backtracking from scratch");
                result = backtrackingScheduler.resolve(context, new PartialSchedule(), callback);
            } else {
                if (callback != null) callback.log("INFO", "Greedy incomplete ("
                        + greedyResult.size() + "/" + required + "), resolving with backtracking");
                LOG.fine("Greedy incomplete (" + greedyResult.size() + "/" + required + "), resolving with backtracking");
                result = backtrackingScheduler.resolve(context, greedyResult, callback);
            }
        }

        HardConstraintValidator.ValidationResult backtrackValidation = hardValidator.validateComplete(result.schedule(), context);
        if (!backtrackValidation.isValid() && callback != null) {
            for (String err : backtrackValidation.errors()) {
                callback.log("WARN", "Backtrack validation: " + err);
            }
        }

        if (callback != null && callback.isCancelled()) {
            callback.log("INFO", "Generation cancelled before optimization phase");
            return result;
        }

        if (optimizationStrategy.isPresent()) {
            LOG.fine("Running optimization strategy");
            if (callback != null) callback.log("INFO", "Running optimization strategy");
            result = optimizationStrategy.get().optimize(result, context);

            HardConstraintValidator.ValidationResult optValidation = hardValidator.validateComplete(result.schedule(), context);
            if (!optValidation.isValid() && callback != null) {
                for (String err : optValidation.errors()) {
                    callback.log("WARN", "Post-optimization validation: " + err);
                }
            }
        }

        long elapsed = System.currentTimeMillis() - start;
        if (callback != null) {
            callback.log("INFO", "Generation finished in " + elapsed + "ms: success=" + result.isSuccess()
                    + ", placed=" + result.schedule().size() + "/" + required
                    + ", quality=" + String.format("%.3f", result.qualityScore()));
            callback.complete(result.isSuccess(), result.schedule().size(), required, result.qualityScore());
        }
        LOG.info("Generation finished in " + elapsed + "ms: success=" + result.isSuccess()
                + ", placed=" + result.schedule().size() + "/" + required
                + ", quality=" + String.format("%.3f", result.qualityScore()));
        return result;
    }

    public List<String> preflightChecks(SchedulingContext context) {
        List<String> issues = new ArrayList<>();
        int totalSlots = context.totalSlots();

        Map<Long, Integer> classLoads = new HashMap<>();
        Map<Long, Integer> teacherLoads = new HashMap<>();
        for (var a : context.assignments()) {
            classLoads.merge(a.getClassStreamId(), a.getLessonsPerWeek(), Integer::sum);
            teacherLoads.merge(a.getTeacherId(), a.getLessonsPerWeek(), Integer::sum);
        }

        for (var entry : classLoads.entrySet()) {
            int load = entry.getValue();
            if (load > totalSlots) {
                String name = context.classStream(entry.getKey())
                        .map(ClassStream::getDisplayName).orElse("ID " + entry.getKey());
                issues.add("Class " + name + " requires " + load
                        + " lessons, exceeding available slots (" + totalSlots + ")");
            }
        }

        for (var entry : teacherLoads.entrySet()) {
            long teacherId = entry.getKey();
            int load = entry.getValue();
            if (load > totalSlots) {
                String name = context.teacher(teacherId).map(Teacher::getName).orElse("ID " + teacherId);
                issues.add("Teacher " + name + " has " + load + " lessons, exceeding available slots (" + totalSlots + ")");
            }
        }
        return issues;
    }
}
