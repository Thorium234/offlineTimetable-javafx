package com.thorium.domain.scheduling;

import com.thorium.domain.constraint.HardConstraintValidator;
import com.thorium.domain.constraint.SoftConstraintScorer;
import com.thorium.domain.model.ScheduleSlot;
import com.thorium.domain.model.TeachingAssignment;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class BacktrackingScheduler {

    private static final int MAX_ITERATIONS = 100_000;

    private final HardConstraintValidator hardValidator;
    private final SoftConstraintScorer softScorer;

    public BacktrackingScheduler(HardConstraintValidator hardValidator, SoftConstraintScorer softScorer) {
        this.hardValidator = hardValidator;
        this.softScorer = softScorer;
    }

    public TimetableGenerationResult resolve(SchedulingContext context, PartialSchedule initial) {
        TimetableGenerationResult result = tryResolve(context, initial, Tier.STRICT);
        if (result != null) return result;

        result = tryResolve(context, initial, Tier.RELAXED);
        if (result != null) return result;

        return TimetableGenerationResult.failure(
                List.of("Backtracking solver failed to find a valid layout under any constraint tier."));
    }

    private TimetableGenerationResult tryResolve(SchedulingContext context, PartialSchedule initial, Tier tier) {
        Map<Long, Long> placedCounts = new HashMap<>();
        for (var lesson : initial.placedLessons()) {
            placedCounts.merge(lesson.assignment().getId(), 1L, Long::sum);
        }

        List<GreedyScheduler.AssignmentWorkItem> allItems =
                new GreedyScheduler(hardValidator, softScorer).expandAssignments(context);
        List<GreedyScheduler.AssignmentWorkItem> workItems = new ArrayList<>();
        for (var item : allItems) {
            long placed = placedCounts.getOrDefault(item.assignment().getId(), 0L);
            if (item.lessonIndex() >= placed) {
                workItems.add(item);
            }
        }

        DomainTracker tracker = new DomainTracker(workItems, context, hardValidator, tier);

        for (var placed : initial.placedLessons()) {
            long taId = placed.assignment().getId();
            for (var item : workItems) {
                if (item.assignment().getId() == taId && item.lessonIndex() == placedCounts.getOrDefault(taId, 0L) - 1) {
                    tracker.markAssigned(item);
                    break;
                }
            }
            tracker.pruneBySlotStatic(placed.assignment().getTeacherId(),
                    placed.assignment().getClassStreamId(), placed.slot());
        }

        PartialSchedule schedule = initial.copy();
        int[] iterations = new int[1];
        List<String> warnings = new ArrayList<>();

        boolean success = search(tracker, schedule, context, iterations, tier);

        if (iterations[0] >= MAX_ITERATIONS) {
            warnings.add("Backtracking stopped after reaching safety limit of " + MAX_ITERATIONS + " search steps.");
        }

        if (!success) return null;

        double quality = softScorer.score(schedule, context);
        HardConstraintValidator.ValidationResult validation = hardValidator.validateComplete(schedule, context);

        if (!validation.isValid()) {
            return TimetableGenerationResult.partial(schedule, quality, validation.errors());
        }

        TimetableGenerationResult result = TimetableGenerationResult.success(schedule, quality);
        if (!result.isComplete(context)) {
            List<String> countViolations = result.validateLessonCounts(context);
            return TimetableGenerationResult.partial(schedule, quality, merge(warnings, countViolations));
        }

        if (!warnings.isEmpty()) {
            return TimetableGenerationResult.partial(schedule, quality, warnings);
        }

        return result;
    }

    private boolean search(DomainTracker tracker, PartialSchedule schedule,
                           SchedulingContext context, int[] iterations, Tier tier) {
        if (tracker.allAssigned()) return true;

        iterations[0]++;
        if (iterations[0] >= MAX_ITERATIONS) return false;

        GreedyScheduler.AssignmentWorkItem item = tracker.selectMRV();
        if (item == null) return false;

        Set<ScheduleSlot> domain = tracker.domains.get(item);
        if (domain == null || domain.isEmpty()) return false;

        List<ScheduleSlot> candidates = domain.stream()
                .filter(slot -> tier == Tier.STRICT
                        ? hardValidator.canPlace(item.assignment(), slot, schedule, context)
                        : passesCoreConstraints(item.assignment(), slot, schedule, context))
                .sorted(Comparator.<ScheduleSlot, Double>comparing(
                        slot -> -softScorer.scorePlacement(item.assignment(), slot, schedule, context))
                        .thenComparingDouble(slot -> (double) tracker.countPrunedByPlacement(item, slot)))
                .toList();

        for (ScheduleSlot slot : candidates) {
            PlacedLesson placed = new PlacedLesson(item.assignment(), slot);
            schedule.place(placed);

            List<PrunedPair> pruned = tracker.placeAndPrune(item, slot);

            if (!tracker.hasEmptyDomain()) {
                if (search(tracker, schedule, context, iterations, tier)) {
                    return true;
                }
            }

            tracker.restore(pruned, item);
            schedule.removeLast();
        }

        return false;
    }

    private enum Tier { STRICT, RELAXED }

    private record PrunedPair(GreedyScheduler.AssignmentWorkItem item, ScheduleSlot slot) {}

    private static class DomainTracker {
        final Map<GreedyScheduler.AssignmentWorkItem, Set<ScheduleSlot>> domains;
        final Map<Long, List<GreedyScheduler.AssignmentWorkItem>> byTeacher;
        final Map<Long, List<GreedyScheduler.AssignmentWorkItem>> byClass;
        final Map<GreedyScheduler.AssignmentWorkItem, Integer> degreeCache;
        final Set<GreedyScheduler.AssignmentWorkItem> assigned;
        final Deque<List<PrunedPair>> history;

        DomainTracker(List<GreedyScheduler.AssignmentWorkItem> items, SchedulingContext context,
                      HardConstraintValidator hardValidator, Tier tier) {
            this.domains = new HashMap<>();
            this.byTeacher = new HashMap<>();
            this.byClass = new HashMap<>();
            this.degreeCache = new HashMap<>();
            this.assigned = new HashSet<>();
            this.history = new ArrayDeque<>();

            List<ScheduleSlot> allSlots = context.allSlots();

            for (var item : items) {
                TeachingAssignment ta = item.assignment();
                Set<ScheduleSlot> valid = new HashSet<>();
                for (ScheduleSlot slot : allSlots) {
                    if (context.isTeacherUnavailable(ta.getTeacherId(), slot)) continue;
                    valid.add(slot);
                }
                domains.put(item, valid);
                byTeacher.computeIfAbsent(ta.getTeacherId(), k -> new ArrayList<>()).add(item);
                byClass.computeIfAbsent(ta.getClassStreamId(), k -> new ArrayList<>()).add(item);
            }

            for (var item : domains.keySet()) {
                degreeCache.put(item, computeDegree(item));
            }
        }

        GreedyScheduler.AssignmentWorkItem selectMRV() {
            GreedyScheduler.AssignmentWorkItem best = null;
            int minSize = Integer.MAX_VALUE;
            int maxDegree = -1;
            for (var entry : domains.entrySet()) {
                if (assigned.contains(entry.getKey())) continue;
                int size = entry.getValue().size();
                if (size == 0) return null;
                int deg = degreeCache.getOrDefault(entry.getKey(), 0);
                if (size < minSize || (size == minSize && deg > maxDegree)) {
                    minSize = size;
                    maxDegree = deg;
                    best = entry.getKey();
                }
            }
            return best;
        }

        int countPrunedByPlacement(GreedyScheduler.AssignmentWorkItem item, ScheduleSlot slot) {
            long teacherId = item.assignment().getTeacherId();
            long classId = item.assignment().getClassStreamId();
            int count = 0;
            for (var other : byTeacher.getOrDefault(teacherId, List.of())) {
                if (!other.equals(item) && !assigned.contains(other)) {
                    Set<ScheduleSlot> d = domains.get(other);
                    if (d != null && d.contains(slot)) count++;
                }
            }
            for (var other : byClass.getOrDefault(classId, List.of())) {
                if (!other.equals(item) && !assigned.contains(other)) {
                    Set<ScheduleSlot> d = domains.get(other);
                    if (d != null && d.contains(slot)) count++;
                }
            }
            return count;
        }

        List<PrunedPair> placeAndPrune(GreedyScheduler.AssignmentWorkItem placed, ScheduleSlot slot) {
            assigned.add(placed);
            domains.get(placed).clear();

            List<PrunedPair> pruned = new ArrayList<>();
            long teacherId = placed.assignment().getTeacherId();
            long classId = placed.assignment().getClassStreamId();

            for (var other : byTeacher.getOrDefault(teacherId, List.of())) {
                if (assigned.contains(other)) continue;
                Set<ScheduleSlot> d = domains.get(other);
                if (d != null && d.remove(slot)) {
                    pruned.add(new PrunedPair(other, slot));
                }
            }

            for (var other : byClass.getOrDefault(classId, List.of())) {
                if (assigned.contains(other)) continue;
                Set<ScheduleSlot> d = domains.get(other);
                if (d != null && d.remove(slot)) {
                    pruned.add(new PrunedPair(other, slot));
                }
            }

            history.push(pruned);
            return pruned;
        }

        void restore(List<PrunedPair> pruned, GreedyScheduler.AssignmentWorkItem item) {
            if (!history.isEmpty()) history.pop();
            for (PrunedPair p : pruned) {
                Set<ScheduleSlot> d = domains.get(p.item());
                if (d != null) d.add(p.slot());
            }
            assigned.remove(item);
        }

        void markAssigned(GreedyScheduler.AssignmentWorkItem item) {
            assigned.add(item);
            Set<ScheduleSlot> d = domains.get(item);
            if (d != null) d.clear();
        }

        void pruneBySlotStatic(long teacherId, long classId, ScheduleSlot slot) {
            for (var other : byTeacher.getOrDefault(teacherId, List.of())) {
                if (assigned.contains(other)) continue;
                Set<ScheduleSlot> d = domains.get(other);
                if (d != null) d.remove(slot);
            }
            for (var other : byClass.getOrDefault(classId, List.of())) {
                if (assigned.contains(other)) continue;
                Set<ScheduleSlot> d = domains.get(other);
                if (d != null) d.remove(slot);
            }
        }

        boolean hasEmptyDomain() {
            for (var entry : domains.entrySet()) {
                if (!assigned.contains(entry.getKey()) && entry.getValue().isEmpty()) {
                    return true;
                }
            }
            return false;
        }

        boolean allAssigned() {
            for (var key : domains.keySet()) {
                if (!assigned.contains(key)) return false;
            }
            return true;
        }

        private int computeDegree(GreedyScheduler.AssignmentWorkItem item) {
            TeachingAssignment ta = item.assignment();
            int deg = 0;
            for (var other : domains.keySet()) {
                if (other.equals(item) || assigned.contains(other)) continue;
                TeachingAssignment oa = other.assignment();
                if (oa.getTeacherId() == ta.getTeacherId() ||
                    oa.getClassStreamId() == ta.getClassStreamId()) {
                    deg++;
                }
            }
            return deg;
        }
    }

    static boolean passesCoreConstraints(TeachingAssignment assignment, ScheduleSlot slot,
                                          PartialSchedule schedule, SchedulingContext context) {
        if (context.isTeacherUnavailable(assignment.getTeacherId(), slot)) return false;
        for (PlacedLesson p : schedule.placedLessons()) {
            if (!p.slot().equals(slot)) continue;
            if (p.assignment().getTeacherId().equals(assignment.getTeacherId())) return false;
            if (p.assignment().getClassStreamId().equals(assignment.getClassStreamId())) return false;
        }
        long count = schedule.placedLessons().stream()
                .filter(p -> p.assignment().getId().equals(assignment.getId()))
                .count();
        return count < assignment.getLessonsPerWeek();
    }

    private List<String> merge(List<String> a, List<String> b) {
        List<String> merged = new ArrayList<>(a);
        merged.addAll(b);
        return merged;
    }
}
