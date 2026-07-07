package com.thorium.domain.scheduling.optimization;

import com.thorium.domain.constraint.HardConstraintValidator;
import com.thorium.domain.constraint.SoftConstraintScorer;
import com.thorium.domain.scheduling.PartialSchedule;
import com.thorium.domain.scheduling.PlacedLesson;
import com.thorium.domain.scheduling.SchedulingContext;
import com.thorium.domain.scheduling.TimetableGenerationResult;

import java.util.*;

public class TabuSearchStrategy implements OptimizationStrategy {

    private static final int MAX_ITERATIONS = 10000;
    private static final int TABU_TENURE = 15;
    private static final int MAX_STAGNATION = 500;
    private static final double ASPIRATION_BONUS = 0.05;

    private final HardConstraintValidator hardValidator;
    private final SoftConstraintScorer softScorer;
    private final Random random;

    public TabuSearchStrategy() {
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
        PartialSchedule bestSchedule = current.copy();
        double bestScore = currentScore;

        TabuList tabuList = new TabuList(TABU_TENURE);
        int stagnationCount = 0;

        for (int iteration = 0; iteration < MAX_ITERATIONS; iteration++) {
            if (Thread.interrupted()) break;
            List<SchedulingMove> candidates = generateCandidates(current, context, tabuList, 30);
            if (candidates.isEmpty()) {
                stagnationCount++;
                if (stagnationCount > MAX_STAGNATION) break;
                continue;
            }

            SchedulingMove bestMove = null;
            double bestNeighborScore = Double.NEGATIVE_INFINITY;

            for (SchedulingMove move : candidates) {
                double score = SchedulingMoveUtils.evaluateMove(move, current, context, softScorer);
                boolean isTabu = tabuList.contains(move);
                boolean aspired = isTabu && score > bestScore + ASPIRATION_BONUS;

                if (score > bestNeighborScore && (!isTabu || aspired)) {
                    bestNeighborScore = score;
                    bestMove = move;
                }
            }

            if (bestMove == null) {
                stagnationCount++;
                if (stagnationCount > MAX_STAGNATION) break;
                continue;
            }

            current = SchedulingMoveUtils.applyMove(bestMove, current);
            currentScore = bestNeighborScore;

            tabuList.add(bestMove);
            tabuList.add(new SchedulingMove(bestMove.lesson2Idx(), bestMove.lesson1Idx(), bestMove.slot2(), bestMove.slot1()));

            if (currentScore > bestScore + 0.001) {
                bestSchedule = current.copy();
                bestScore = currentScore;
                stagnationCount = 0;
            } else {
                stagnationCount++;
                if (stagnationCount > MAX_STAGNATION) break;
            }
        }

        return TimetableGenerationResult.success(bestSchedule, bestScore);
    }

    private List<SchedulingMove> generateCandidates(PartialSchedule schedule, SchedulingContext context,
                                                      TabuList tabuList, int maxCandidates) {
        List<PlacedLesson> lessons = new ArrayList<>(schedule.placedLessons());
        if (lessons.size() < 2) return List.of();

        Set<String> seen = new HashSet<>();
        List<SchedulingMove> candidates = new ArrayList<>();
        int attempts = 0;

        while (candidates.size() < maxCandidates && attempts < maxCandidates * 5) {
            attempts++;
            int idx1 = random.nextInt(lessons.size());
            int idx2 = random.nextInt(lessons.size());
            if (idx1 == idx2) continue;

            PlacedLesson l1 = lessons.get(idx1);
            PlacedLesson l2 = lessons.get(idx2);
            String key = Math.min(idx1, idx2) + "-" + Math.max(idx1, idx2);
            if (!seen.add(key)) continue;

            PartialSchedule trial = schedule.copy();
            trial.remove(l1);
            trial.remove(l2);

            PlacedLesson swapped1 = new PlacedLesson(l1.assignment(), l2.slot());
            PlacedLesson swapped2 = new PlacedLesson(l2.assignment(), l1.slot());

            if (!hardValidator.canPlace(swapped1.assignment(), swapped1.slot(), trial, context)
                    || !hardValidator.canPlace(swapped2.assignment(), swapped2.slot(), trial, context)) {
                continue;
            }
            trial.place(swapped1);
            trial.place(swapped2);
            if (!SchedulingMoveUtils.countsValid(trial, context)) continue;

            candidates.add(new SchedulingMove(idx1, idx2, l2.slot(), l1.slot()));
        }

        return candidates;
    }

}
