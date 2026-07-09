package com.thorium.domain.scheduling.optimization;

import com.thorium.domain.constraint.HardConstraintValidator;
import com.thorium.domain.constraint.SoftConstraintScorer;
import com.thorium.domain.model.LessonDuration;
import com.thorium.domain.model.ScheduleSlot;
import com.thorium.domain.scheduling.PartialSchedule;
import com.thorium.domain.scheduling.PlacedLesson;
import com.thorium.domain.scheduling.SchedulingContext;
import com.thorium.domain.scheduling.TimetableGenerationResult;

import java.util.*;

public class HybridMetaheuristicStrategy implements OptimizationStrategy {

    private static final int TABU_ITERATIONS = 5000;
    private static final int GA_GENERATIONS = 50;
    private static final int TABU_TENURE = 12;
    private static final int POPULATION_SIZE = 30;
    private static final int ELITISM_COUNT = 3;
    private static final double MUTATION_RATE = 0.12;
    private static final double CROSSOVER_RATE = 0.80;
    private static final int TOURNAMENT_SIZE = 4;
    private static final int STAGNATION_LIMIT = 15;
    private static final int HARMONY_MEMORY_SIZE = 20;
    private static final double HM_CONSIDERATION_RATE = 0.85;
    private static final double PITCH_ADJUSTMENT_RATE = 0.10;

    private final HardConstraintValidator hardValidator;
    private final SoftConstraintScorer softScorer;
    private final Random random;
    private final TabuSearchStrategy tabuSearch;
    private final GeneticAlgorithmStrategy gaSearch;

    public HybridMetaheuristicStrategy() {
        this.hardValidator = new HardConstraintValidator();
        this.softScorer = new SoftConstraintScorer();
        this.random = new Random();
        this.tabuSearch = new TabuSearchStrategy();
        this.gaSearch = new GeneticAlgorithmStrategy();
    }

    @Override
    public TimetableGenerationResult optimize(TimetableGenerationResult initial, SchedulingContext context) {
        if (!initial.isSuccess()) {
            return initial;
        }

        TimetableGenerationResult best = initial;
        double bestScore = softScorer.score(initial.schedule(), context);

        best = runPhaseTabuSearch(initial, context, best, bestScore);
        bestScore = softScorer.score(best.schedule(), context);

        best = runPhaseHarmonySearch(initial, context, best, bestScore);
        bestScore = softScorer.score(best.schedule(), context);

        TimetableGenerationResult gaResult = gaSearch.optimize(best, context);
        double gaScore = softScorer.score(gaResult.schedule(), context);
        if (gaScore > bestScore) {
            best = gaResult;
            bestScore = gaScore;
        }

        best = runPhaseScatterSearch(best, context, bestScore);

        return best;
    }

    private TimetableGenerationResult runPhaseTabuSearch(
            TimetableGenerationResult initial, SchedulingContext context,
            TimetableGenerationResult best, double bestScore) {

        PartialSchedule current = initial.schedule().copy();
        double currentScore = softScorer.score(current, context);
        PartialSchedule bestSchedule = best.schedule().copy();

        TabuList tabuList = new TabuList(TABU_TENURE);
        int stagnationCount = 0;

        for (int iteration = 0; iteration < TABU_ITERATIONS; iteration++) {
            List<SchedulingMove> candidates = generateCandidates(current, context, tabuList, 20);
            if (candidates.isEmpty()) {
                stagnationCount++;
                if (stagnationCount > STAGNATION_LIMIT) break;
                continue;
            }

            SchedulingMove bestMove = null;
            double bestMoveScore = Double.NEGATIVE_INFINITY;

            for (SchedulingMove move : candidates) {
                double score = SchedulingMoveUtils.evaluateMove(move, current, context, softScorer);
                boolean isTabu = tabuList.contains(move);
                boolean aspired = isTabu && score > bestScore + 0.05;

                if (score > bestMoveScore && (!isTabu || aspired)) {
                    bestMoveScore = score;
                    bestMove = move;
                }
            }

            if (bestMove == null) {
                stagnationCount++;
                if (stagnationCount > STAGNATION_LIMIT) break;
                continue;
            }

            current = SchedulingMoveUtils.applyMove(bestMove, current);
            currentScore = bestMoveScore;
            tabuList.add(bestMove);
            tabuList.add(new SchedulingMove(bestMove.lesson2Idx(), bestMove.lesson1Idx(),
                    bestMove.slot2(), bestMove.slot1()));

            if (currentScore > bestScore + 0.001) {
                bestSchedule = current.copy();
                bestScore = currentScore;
                stagnationCount = 0;
            } else {
                stagnationCount++;
            }
        }

        return TimetableGenerationResult.success(bestSchedule, bestScore);
    }

    private TimetableGenerationResult runPhaseHarmonySearch(
            TimetableGenerationResult initial, SchedulingContext context,
            TimetableGenerationResult best, double bestScore) {

        List<PartialSchedule> harmonyMemory = new ArrayList<>();
        harmonyMemory.add(best.schedule().copy());
        for (int i = 1; i < HARMONY_MEMORY_SIZE; i++) {
            PartialSchedule perturbed = perturbSchedule(initial.schedule(), context, 3);
            if (perturbed != null) {
                harmonyMemory.add(perturbed);
            }
        }

        harmonyMemory.sort(Comparator.<PartialSchedule>comparingDouble(
                s -> softScorer.score(s, context)).reversed());
        harmonyMemory = harmonyMemory.subList(0, Math.min(HARMONY_MEMORY_SIZE, harmonyMemory.size()));

        int stagnationCount = 0;
        for (int iteration = 0; iteration < 200; iteration++) {
            PartialSchedule newHarmony = new PartialSchedule();
            Set<String> placedAssignments = new HashSet<>();

            List<ScheduleSlot> allSlots = context.allSlots();
            List<PlacedLesson> reference = harmonyMemory.get(random.nextInt(harmonyMemory.size())).placedLessons();

            for (PlacedLesson lesson : reference) {
                if (random.nextDouble() < HM_CONSIDERATION_RATE) {
                    PlacedLesson candidate = new PlacedLesson(lesson.assignment(), lesson.slot());
                    if (canPlace(candidate, newHarmony, context, placedAssignments)) {
                        newHarmony.place(candidate);
                        placedAssignments.add(candidate.assignment().getId() + "-" + candidate.assignment().getLessonsPerWeek());
                    }
                }
            }

            if (random.nextDouble() < PITCH_ADJUSTMENT_RATE) {
                List<PlacedLesson> newLessons = new ArrayList<>(newHarmony.placedLessons());
                if (!newLessons.isEmpty()) {
                    int idx = random.nextInt(newLessons.size());
                    PlacedLesson toAdjust = newLessons.get(idx);
                    newHarmony.remove(toAdjust);
                    placedAssignments.remove(toAdjust.assignment().getId() + "-" + toAdjust.assignment().getLessonsPerWeek());

                    ScheduleSlot randomSlot = allSlots.get(random.nextInt(allSlots.size()));
                    PlacedLesson adjusted = new PlacedLesson(toAdjust.assignment(), randomSlot);
                    if (canPlace(adjusted, newHarmony, context, placedAssignments)) {
                        newHarmony.place(adjusted);
                        placedAssignments.add(adjusted.assignment().getId() + "-" + adjusted.assignment().getLessonsPerWeek());
                    } else {
                        newHarmony.place(toAdjust);
                        placedAssignments.add(toAdjust.assignment().getId() + "-" + toAdjust.assignment().getLessonsPerWeek());
                    }
                }
            }

            fillRemainingSlots(newHarmony, context, placedAssignments);

            double newScore = softScorer.score(newHarmony, context);
            double worstScore = softScorer.score(harmonyMemory.get(harmonyMemory.size() - 1), context);

            if (newScore > worstScore) {
                harmonyMemory.add(newHarmony);
                harmonyMemory.sort(Comparator.<PartialSchedule>comparingDouble(
                        s -> softScorer.score(s, context)).reversed());
                harmonyMemory = harmonyMemory.subList(0, HARMONY_MEMORY_SIZE);
            }

            double currentBest = softScorer.score(harmonyMemory.getFirst(), context);
            if (currentBest > bestScore + 0.001) {
                bestScore = currentBest;
                stagnationCount = 0;
            } else {
                stagnationCount++;
                if (stagnationCount > STAGNATION_LIMIT * 2) break;
            }
        }

        return TimetableGenerationResult.success(harmonyMemory.getFirst(), bestScore);
    }

    private TimetableGenerationResult runPhaseScatterSearch(
            TimetableGenerationResult best, SchedulingContext context, double bestScore) {

        List<PartialSchedule> referenceSet = new ArrayList<>();
        referenceSet.add(best.schedule().copy());
        for (int i = 0; i < 9; i++) {
            PartialSchedule perturbed = perturbSchedule(best.schedule(), context, 5 + i);
            if (perturbed != null) {
                referenceSet.add(perturbed);
            }
        }

        int stagnationCount = 0;
        for (int iteration = 0; iteration < 100; iteration++) {
            int i1 = random.nextInt(referenceSet.size());
            int i2 = random.nextInt(referenceSet.size());
            if (i1 == i2) continue;

            PartialSchedule combined = combineSchedules(referenceSet.get(i1), referenceSet.get(i2), context);
            double combinedScore = softScorer.score(combined, context);

            PartialSchedule improved = localSearch(combined, context, 100);
            double improvedScore = softScorer.score(improved, context);

            double worstRef = softScorer.score(referenceSet.get(referenceSet.size() - 1), context);
            if (improvedScore > worstRef) {
                referenceSet.add(improved);
                referenceSet.sort(Comparator.<PartialSchedule>comparingDouble(
                        s -> softScorer.score(s, context)).reversed());
                referenceSet = referenceSet.subList(0, 10);
            }

            if (improvedScore > bestScore + 0.001) {
                bestScore = improvedScore;
                stagnationCount = 0;
            } else {
                stagnationCount++;
                if (stagnationCount > STAGNATION_LIMIT) break;
            }
        }

        return TimetableGenerationResult.success(referenceSet.getFirst(), bestScore);
    }

    private PartialSchedule combineSchedules(PartialSchedule s1, PartialSchedule s2, SchedulingContext context) {
        PartialSchedule combined = new PartialSchedule();
        Set<String> usedSlots = new HashSet<>();

        List<PlacedLesson> lessons1 = s1.placedLessons();
        List<PlacedLesson> lessons2 = s2.placedLessons();

        for (PlacedLesson lesson : lessons1) {
            String key = lesson.assignment().getId() + "@" + lesson.slot();
            if (!usedSlots.contains(key) && canPlaceInCombined(lesson, combined, context)) {
                combined.place(lesson);
                usedSlots.add(key);
            }
        }

        for (PlacedLesson lesson : lessons2) {
            String key = lesson.assignment().getId() + "@" + lesson.slot();
            if (!usedSlots.contains(key) && canPlaceInCombined(lesson, combined, context)) {
                combined.place(lesson);
                usedSlots.add(key);
            }
        }

        return combined;
    }

    private boolean canPlaceInCombined(PlacedLesson lesson, PartialSchedule schedule, SchedulingContext context) {
        for (PlacedLesson existing : schedule.placedLessons()) {
            if (!existing.slot().equals(lesson.slot())) continue;
            if (existing.assignment().getTeacherId().equals(lesson.assignment().getTeacherId())) return false;
            if (existing.assignment().getClassStreamId().equals(lesson.assignment().getClassStreamId())) return false;
        }
        if (context.isTeacherUnavailable(lesson.assignment().getTeacherId(), lesson.slot())) return false;
        long count = schedule.placedLessons().stream()
                .filter(p -> p.assignment().getId().equals(lesson.assignment().getId()))
                .count();
        return count < lesson.assignment().getLessonsPerWeek();
    }

    private PartialSchedule localSearch(PartialSchedule schedule, SchedulingContext context, int maxIterations) {
        PartialSchedule current = schedule.copy();
        double currentScore = softScorer.score(current, context);

        for (int iter = 0; iter < maxIterations; iter++) {
            List<PlacedLesson> lessons = new ArrayList<>(current.placedLessons());
            if (lessons.size() < 2) break;

            int idx1 = random.nextInt(lessons.size());
            int idx2 = random.nextInt(lessons.size());
            if (idx1 == idx2) continue;

            PlacedLesson l1 = lessons.get(idx1);
            PlacedLesson l2 = lessons.get(idx2);

            PartialSchedule trial = current.copy();
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

            double newScore = softScorer.score(trial, context);
            if (newScore > currentScore) {
                current = trial;
                currentScore = newScore;
            }
        }

        return current;
    }

    private List<SchedulingMove> generateCandidates(PartialSchedule schedule, SchedulingContext context,
                                                     TabuList tabuList, int maxCandidates) {
        return SchedulingMoveUtils.generateSwapCandidates(schedule, context, hardValidator, maxCandidates);
    }

    private PartialSchedule perturbSchedule(PartialSchedule schedule, SchedulingContext context, int perturbations) {
        PartialSchedule result = schedule.copy();
        for (int p = 0; p < perturbations; p++) {
            List<PlacedLesson> lessons = new ArrayList<>(result.placedLessons());
            if (lessons.size() < 2) return result;

            int idx1 = random.nextInt(lessons.size());
            int idx2 = random.nextInt(lessons.size());
            if (idx1 == idx2) continue;

            PlacedLesson l1 = lessons.get(idx1);
            PlacedLesson l2 = lessons.get(idx2);
            result.remove(l1);
            result.remove(l2);

            PlacedLesson swapped1 = new PlacedLesson(l1.assignment(), l2.slot());
            PlacedLesson swapped2 = new PlacedLesson(l2.assignment(), l1.slot());

            if (hardValidator.canPlace(swapped1.assignment(), swapped1.slot(), result, context)
                    && hardValidator.canPlace(swapped2.assignment(), swapped2.slot(), result, context)) {
                result.place(swapped1);
                result.place(swapped2);
            } else {
                result.place(l1);
                result.place(l2);
            }
        }
        return result;
    }

    private boolean canPlace(PlacedLesson candidate, PartialSchedule schedule,
                             SchedulingContext context, Set<String> placedAssignments) {
        String assignmentKey = candidate.assignment().getId() + "-" + candidate.assignment().getLessonsPerWeek();
        if (placedAssignments.contains(assignmentKey)) return false;

        for (PlacedLesson existing : schedule.placedLessons()) {
            if (!existing.slot().equals(candidate.slot())) continue;
            if (existing.assignment().getTeacherId().equals(candidate.assignment().getTeacherId())) return false;
            if (existing.assignment().getClassStreamId().equals(candidate.assignment().getClassStreamId())) return false;
        }
        if (context.isTeacherUnavailable(candidate.assignment().getTeacherId(), candidate.slot())) return false;
        return true;
    }

    private void fillRemainingSlots(PartialSchedule schedule, SchedulingContext context, Set<String> placedAssignments) {
        for (var assignment : context.assignments()) {
            String key = assignment.getId() + "-" + assignment.getLessonsPerWeek();
            if (placedAssignments.contains(key)) continue;

            int needed = assignment.getLessonsPerWeek();
            boolean isDouble = assignment.getDuration() == LessonDuration.DOUBLE;
            int neededItems = isDouble ? needed / 2 : needed;

            List<ScheduleSlot> slots = new ArrayList<>(context.allSlots());
            Collections.shuffle(slots, random);

            for (int i = 0; i < neededItems && !slots.isEmpty(); i++) {
                ScheduleSlot bestSlot = null;
                double bestScore = Double.NEGATIVE_INFINITY;

                for (ScheduleSlot slot : slots) {
                    int limit = isDouble ? context.periodsPerDay() - 1 : context.periodsPerDay();
                    int idx = context.indexOfLessonPeriod(slot.periodNumber());
                    if (idx < 0 || idx >= limit) continue;

                    PlacedLesson candidate = new PlacedLesson(assignment, slot);
                    if (!canPlace(candidate, schedule, context, placedAssignments)) continue;

                    if (isDouble) {
                        ScheduleSlot next = context.nextLessonSlot(slot);
                        if (next == null) continue;
                        if (!canPlace(new PlacedLesson(assignment, next), schedule, context, placedAssignments)) continue;
                    }

                    double score = softScorer.scorePlacement(assignment, slot, schedule, context);
                    if (score > bestScore) {
                        bestScore = score;
                        bestSlot = slot;
                    }
                }

                if (bestSlot != null) {
                    schedule.place(new PlacedLesson(assignment, bestSlot));
                    if (isDouble) {
                        ScheduleSlot next = context.nextLessonSlot(bestSlot);
                        if (next != null) {
                            schedule.place(new PlacedLesson(assignment, next));
                        }
                    }
                }
            }
        }
    }

}
