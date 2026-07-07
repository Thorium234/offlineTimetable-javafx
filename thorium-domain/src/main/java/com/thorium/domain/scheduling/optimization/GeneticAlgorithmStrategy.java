package com.thorium.domain.scheduling.optimization;

import com.thorium.domain.constraint.HardConstraintValidator;
import com.thorium.domain.constraint.SoftConstraintScorer;
import com.thorium.domain.model.LessonDuration;
import com.thorium.domain.model.ScheduleSlot;
import com.thorium.domain.model.TeachingAssignment;
import com.thorium.domain.scheduling.PartialSchedule;
import com.thorium.domain.scheduling.PlacedLesson;
import com.thorium.domain.scheduling.SchedulingContext;
import com.thorium.domain.scheduling.TimetableGenerationResult;

import java.util.*;

public class GeneticAlgorithmStrategy implements OptimizationStrategy {

    private static final int POPULATION_SIZE = 50;
    private static final int MAX_GENERATIONS = 100;
    private static final double MUTATION_RATE = 0.15;
    private static final double CROSSOVER_RATE = 0.80;
    private static final int TOURNAMENT_SIZE = 5;
    private static final int ELITISM_COUNT = 4;
    private static final int STAGNATION_LIMIT = 20;

    private final HardConstraintValidator hardValidator;
    private final SoftConstraintScorer softScorer;
    private final Random random;

    public GeneticAlgorithmStrategy() {
        this.hardValidator = new HardConstraintValidator();
        this.softScorer = new SoftConstraintScorer();
        this.random = new Random();
    }

    @Override
    public TimetableGenerationResult optimize(TimetableGenerationResult initial, SchedulingContext context) {
        if (!initial.isSuccess()) {
            return initial;
        }

        List<PartialSchedule> population = initializePopulation(initial, context);
        double bestFitness = Double.NEGATIVE_INFINITY;
        PartialSchedule bestIndividual = null;
        int stagnationCount = 0;

        for (int generation = 0; generation < MAX_GENERATIONS; generation++) {
            List<PartialSchedule> newPopulation = new ArrayList<>();

            for (int i = 0; i < ELITISM_COUNT && i < population.size(); i++) {
                newPopulation.add(population.get(i).copy());
            }

            while (newPopulation.size() < POPULATION_SIZE) {
                PartialSchedule parent1 = tournamentSelect(population, context);
                PartialSchedule parent2 = tournamentSelect(population, context);

                PartialSchedule offspring;
                if (random.nextDouble() < CROSSOVER_RATE) {
                    offspring = crossover(parent1, parent2, context);
                } else {
                    offspring = parent1.copy();
                }

                if (random.nextDouble() < MUTATION_RATE) {
                    offspring = mutate(offspring, context);
                }

                newPopulation.add(offspring);
            }

            population = newPopulation.stream()
                    .sorted(Comparator.<PartialSchedule>comparingDouble(
                            s -> softScorer.score(s, context)).reversed())
                    .limit(POPULATION_SIZE)
                    .toList();

            double currentBest = softScorer.score(population.getFirst(), context);
            if (currentBest > bestFitness + 0.001) {
                bestFitness = currentBest;
                bestIndividual = population.getFirst().copy();
                stagnationCount = 0;
            } else {
                stagnationCount++;
                if (stagnationCount >= STAGNATION_LIMIT) break;
            }
        }

        if (bestIndividual != null) {
            return TimetableGenerationResult.success(bestIndividual, bestFitness);
        }
        return initial;
    }

    private List<PartialSchedule> initializePopulation(TimetableGenerationResult initial, SchedulingContext context) {
        List<PartialSchedule> population = new ArrayList<>();
        population.add(initial.schedule().copy());

        for (int i = 1; i < POPULATION_SIZE; i++) {
            PartialSchedule mutated = initial.schedule().copy();
            int mutations = 1 + random.nextInt(5);
            for (int m = 0; m < mutations; m++) {
                mutated = mutate(mutated, context);
                if (mutated == null) break;
            }
            if (mutated != null) {
                population.add(mutated);
            }
        }

        return population.stream()
                .sorted(Comparator.<PartialSchedule>comparingDouble(
                        s -> softScorer.score(s, context)).reversed())
                .limit(POPULATION_SIZE)
                .toList();
    }

    private PartialSchedule crossover(PartialSchedule parent1, PartialSchedule parent2, SchedulingContext context) {
        PartialSchedule offspring = new PartialSchedule();

        Set<Long> fromParent1 = new HashSet<>();
        for (PlacedLesson lesson : parent1.placedLessons()) {
            if (random.nextBoolean()) {
                fromParent1.add(lesson.assignment().getId());
            }
        }

        for (PlacedLesson lesson : parent1.placedLessons()) {
            if (fromParent1.contains(lesson.assignment().getId())) {
                PlacedLesson candidate = new PlacedLesson(lesson.assignment(), lesson.slot());
                if (canPlaceInOffspring(candidate, offspring, context)) {
                    offspring.place(candidate);
                }
            }
        }

        for (PlacedLesson lesson : parent2.placedLessons()) {
            if (!fromParent1.contains(lesson.assignment().getId())) {
                PlacedLesson candidate = new PlacedLesson(lesson.assignment(), lesson.slot());
                if (canPlaceInOffspring(candidate, offspring, context)) {
                    offspring.place(candidate);
                }
            }
        }

        Map<Long, Integer> placedCounts = new HashMap<>();
        for (PlacedLesson lesson : offspring.placedLessons()) {
            placedCounts.merge(lesson.assignment().getId(), 1, Integer::sum);
        }

        for (TeachingAssignment ta : context.assignments()) {
            int placed = placedCounts.getOrDefault(ta.getId(), 0);
            int needed = ta.getLessonsPerWeek() - placed;
            if (needed <= 0) continue;

            List<ScheduleSlot> slots = new ArrayList<>(context.allSlots());
            Collections.shuffle(slots, random);

            boolean isDouble = ta.getDuration() == LessonDuration.DOUBLE;
            int neededItems = isDouble ? needed / 2 : needed;

            for (int i = 0; i < neededItems && !slots.isEmpty(); i++) {
                ScheduleSlot bestSlot = null;
                double bestScore = Double.NEGATIVE_INFINITY;

                for (ScheduleSlot slot : slots) {
                    int limit = isDouble ? context.periodsPerDay() - 1 : context.periodsPerDay();
                    int idx = context.indexOfLessonPeriod(slot.periodNumber());
                    if (idx < 0 || idx >= limit) continue;

                    PlacedLesson candidate = new PlacedLesson(ta, slot);
                    if (!canPlaceInOffspring(candidate, offspring, context)) continue;

                    if (isDouble) {
                        ScheduleSlot next = context.nextLessonSlot(slot);
                        if (next == null) continue;
                        if (!canPlaceInOffspring(new PlacedLesson(ta, next), offspring, context)) continue;
                    }

                    double score = softScorer.scorePlacement(ta, slot, offspring, context);
                    if (score > bestScore) {
                        bestScore = score;
                        bestSlot = slot;
                    }
                }

                if (bestSlot != null) {
                    offspring.place(new PlacedLesson(ta, bestSlot));
                    if (isDouble) {
                        ScheduleSlot next = context.nextLessonSlot(bestSlot);
                        if (next != null) {
                            offspring.place(new PlacedLesson(ta, next));
                        }
                    }
                }
            }
        }

        return offspring;
    }

    private PartialSchedule mutate(PartialSchedule schedule, SchedulingContext context) {
        List<PlacedLesson> lessons = new ArrayList<>(schedule.placedLessons());
        if (lessons.size() < 2) return null;

        PartialSchedule trial = schedule.copy();
        int idx1 = random.nextInt(lessons.size());
        int idx2 = random.nextInt(lessons.size());
        if (idx1 == idx2) return trial;

        PlacedLesson l1 = lessons.get(idx1);
        PlacedLesson l2 = lessons.get(idx2);
        ScheduleSlot s1 = l1.slot();
        ScheduleSlot s2 = l2.slot();

        trial.remove(l1);
        trial.remove(l2);

        PlacedLesson swapped1 = new PlacedLesson(l1.assignment(), s2);
        PlacedLesson swapped2 = new PlacedLesson(l2.assignment(), s1);

        if (!hardValidator.canPlace(swapped1.assignment(), swapped1.slot(), trial, context)
                || !hardValidator.canPlace(swapped2.assignment(), swapped2.slot(), trial, context)) {
            trial.place(l1);
            trial.place(l2);
            return trial;
        }

        trial.place(swapped1);
        trial.place(swapped2);
        return trial;
    }

    private boolean canPlaceInOffspring(PlacedLesson candidate, PartialSchedule schedule, SchedulingContext context) {
        for (PlacedLesson existing : schedule.placedLessons()) {
            if (!existing.slot().equals(candidate.slot())) continue;
            if (existing.assignment().getTeacherId().equals(candidate.assignment().getTeacherId())) return false;
            if (existing.assignment().getClassStreamId().equals(candidate.assignment().getClassStreamId())) return false;
        }
        if (context.isTeacherUnavailable(candidate.assignment().getTeacherId(), candidate.slot())) return false;

        long count = schedule.placedLessons().stream()
                .filter(p -> p.assignment().getId().equals(candidate.assignment().getId()))
                .count();
        return count < candidate.assignment().getLessonsPerWeek();
    }

    private PartialSchedule tournamentSelect(List<PartialSchedule> population, SchedulingContext context) {
        PartialSchedule best = null;
        double bestFitness = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < TOURNAMENT_SIZE; i++) {
            int idx = random.nextInt(population.size());
            double fitness = softScorer.score(population.get(idx), context);
            if (fitness > bestFitness) {
                bestFitness = fitness;
                best = population.get(idx);
            }
        }
        return best != null ? best.copy() : population.getFirst().copy();
    }
}
