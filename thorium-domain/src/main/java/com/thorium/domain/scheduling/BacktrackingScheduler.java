package com.thorium.domain.scheduling;

import com.thorium.domain.constraint.HardConstraintValidator;
import com.thorium.domain.constraint.SoftConstraintScorer;
import com.thorium.domain.exception.SchedulingException;
import com.thorium.domain.model.ScheduleSlot;
import com.thorium.domain.model.TeachingAssignment;
import com.thorium.domain.value.DayOfWeek;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

public class BacktrackingScheduler {

    private static final Logger LOG = Logger.getLogger(BacktrackingScheduler.class.getName());

    private static final int MAX_ITERATIONS = 100_000;

    private final HardConstraintValidator hardValidator;
    private final SoftConstraintScorer softScorer;

    public BacktrackingScheduler(HardConstraintValidator hardValidator, SoftConstraintScorer softScorer) {
        this.hardValidator = hardValidator;
        this.softScorer = softScorer;
    }

    public TimetableGenerationResult resolve(SchedulingContext context, PartialSchedule initial) {
        return resolve(context, initial, null);
    }

    public TimetableGenerationResult resolve(SchedulingContext context, PartialSchedule initial,
                                              GenerationProgressCallback callback) {
        if (callback != null) callback.tierChange("STRICT");
        LOG.fine("Attempting STRICT tier backtracking (initial=" + initial.size() + " placed)");
        TimetableGenerationResult result = tryResolve(context, initial, Tier.STRICT, callback);
        if (result != null) return result;

        if (callback != null) callback.tierChange("RELAXED");
        LOG.fine("STRICT tier failed, attempting RELAXED tier");
        result = tryResolve(context, initial, Tier.RELAXED, callback);
        if (result != null) {
            int placed = result.schedule().size();
            LOG.warning("RELAXED tier placed " + placed + " lessons (partial result)");
            return result;
        }

        String msg = "Backtracking solver failed under all constraint tiers";
        LOG.warning(msg);
        SchedulingException ex = new SchedulingException(msg);
        if (callback != null) {
            callback.log("ERROR", msg);
            callback.complete(false, initial.size(), 0, 0.0);
        }
        return TimetableGenerationResult.failure(List.of(msg));
    }

    private TimetableGenerationResult tryResolve(SchedulingContext context, PartialSchedule initial, Tier tier,
                                                  GenerationProgressCallback callback) {
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

        int emptyDomains = 0;
        int totalItems = 0;
        int totalDomainSize = 0;
        int minDomain = Integer.MAX_VALUE;
        for (var entry : tracker.domains.entrySet()) {
            if (tracker.assigned.contains(entry.getKey())) continue;
            totalItems++;
            int size = entry.getValue().size();
            totalDomainSize += size;
            if (size < minDomain) minDomain = size;
            if (size == 0) emptyDomains++;
        }
        LOG.fine(tier + " tier domain stats: items=" + totalItems
                + ", empty=" + emptyDomains
                + ", minDomain=" + (minDomain == Integer.MAX_VALUE ? "N/A" : minDomain)
                + ", avgDomain=" + (totalItems > 0 ? String.format("%.1f", (double) totalDomainSize / totalItems) : "N/A"));
        if (emptyDomains > 0) {
            LOG.warning(tier + " tier: " + emptyDomains + "/" + totalItems + " work items have empty domains");
        }

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

        if (callback != null) {
            int required = context.assignments().stream().mapToInt(a -> a.getLessonsPerWeek()).sum();
            callback.log("INFO", tier + " tier: " + totalItems + " open items, "
                    + "min domain=" + (minDomain == Integer.MAX_VALUE ? "N/A" : minDomain)
                    + ", avg=" + (totalItems > 0 ? String.format("%.1f", (double) totalDomainSize / totalItems) : "N/A"));
        }

        boolean success = search(tracker, schedule, context, iterations, tier, callback);

        if (iterations[0] >= MAX_ITERATIONS) {
            warnings.add("Backtracking stopped after reaching safety limit of " + MAX_ITERATIONS + " search steps.");
            if (tier == Tier.RELAXED && schedule.size() > 0) {
                LOG.warning("RELAXED tier hit iteration limit with " + schedule.size()
                        + " lessons placed, returning partial result");
                success = true;
            }
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
                            SchedulingContext context, int[] iterations, Tier tier,
                            GenerationProgressCallback callback) {
        if (tracker.allAssigned()) return true;

        iterations[0]++;
        if (iterations[0] >= MAX_ITERATIONS) return false;

        if (callback != null && callback.isCancelled()) {
            LOG.info("Backtracking search cancelled at iteration " + iterations[0]);
            if (callback != null) callback.log("INFO", "Backtracking search cancelled");
            return false;
        }

        GreedyScheduler.AssignmentWorkItem item = tracker.selectMRV();
        if (item == null) {
            String msg = "MRV returned null at iteration " + iterations[0] + " (empty domain detected)";
            LOG.fine(msg);
            if (callback != null) callback.log("WARN", msg);
            if (tier == Tier.RELAXED) return true;
            return false;
        }

        Set<ScheduleSlot> domain = tracker.domains.get(item);
        if (domain == null || domain.isEmpty()) {
            String msg = "Empty domain for item " + item.assignment().getId()
                    + " (lessonIndex=" + item.lessonIndex() + ") at iteration " + iterations[0];
            LOG.fine(msg);
            if (tier == Tier.RELAXED) {
                if (callback != null) callback.log("WARN", "Skipping " + itemSummary(item) + " — " + msg);
                tracker.markSkipped(item);
                return search(tracker, schedule, context, iterations, tier, callback);
            }
            return false;
        }

        List<ScheduleSlot> candidates = domain.stream()
                .filter(slot -> {
                    if (tier == Tier.STRICT) {
                        if (!hardValidator.canPlace(item.assignment(), slot, schedule, context)) return false;
                        if (item.requiresConsecutive()) {
                            ScheduleSlot next = context.nextLessonSlot(slot);
                            if (next == null) return false;
                            if (!hardValidator.canPlace(item.assignment(), next, schedule, context)) return false;
                        }
                        return true;
                    } else {
                        return passesCoreConstraints(item.assignment(), slot, schedule, context);
                    }
                })
                .sorted(Comparator.<ScheduleSlot, Double>comparing(
                        slot -> -softScorer.scorePlacement(item.assignment(), slot, schedule, context))
                        .thenComparingDouble(slot -> (double) tracker.countPrunedByPlacement(item, slot))
                        .thenComparingInt(slot -> countPlacedOnDay(item.assignment().getId(), slot.dayOfWeek(), schedule)))
                .toList();

        if (tier == Tier.RELAXED && candidates.isEmpty()) {
            if (callback != null) callback.log("WARN", "Skipping " + itemSummary(item)
                    + " — no candidates after core filter");
            tracker.markSkipped(item);
            int required = context.assignments().stream().mapToInt(a -> a.getLessonsPerWeek()).sum();
            if (callback != null) callback.progress(schedule.size(), required);
            return search(tracker, schedule, context, iterations, tier, callback);
        }

        for (ScheduleSlot slot : candidates) {
            PlacedLesson placed = new PlacedLesson(item.assignment(), slot);
            schedule.place(placed);

            if (item.requiresConsecutive()) {
                ScheduleSlot next = context.nextLessonSlot(slot);
                if (next == null || !hardValidator.canPlace(item.assignment(), next, schedule, context)) {
                    schedule.removeLast();
                    continue;
                }
                schedule.place(new PlacedLesson(item.assignment(), next));
            }

            List<PrunedPair> pruned = tracker.placeAndPrune(item, slot);

            if (iterations[0] % 1000 == 0 && callback != null) {
                int required = context.assignments().stream().mapToInt(a -> a.getLessonsPerWeek()).sum();
                callback.progress(schedule.size(), required);
                callback.log("INFO", "Search iteration " + iterations[0] + ": placed=" + schedule.size()
                        + " trying " + itemSummary(item) + " at " + slot);
            }

            if (!tracker.hasEmptyDomain()) {
                if (search(tracker, schedule, context, iterations, tier, callback)) {
                    return true;
                }
            }

            tracker.restore(pruned, item);
            PlacedLesson removed = schedule.removeLast();
            if (item.requiresConsecutive()) {
                schedule.removeLast();
            }
        }

        if (tier == Tier.RELAXED) {
            LOG.fine("RELAXED: no valid slot for item " + item.assignment().getId()
                    + " (lessonIndex=" + item.lessonIndex()
                    + "), skipping. Placed so far: " + schedule.size());
            tracker.markSkipped(item);
            int required = context.assignments().stream().mapToInt(a -> a.getLessonsPerWeek()).sum();
            if (callback != null) callback.progress(schedule.size(), required);
            return search(tracker, schedule, context, iterations, tier, callback);
        }
        return false;
    }

    private int countPlacedOnDay(long assignmentId, DayOfWeek day, PartialSchedule schedule) {
        return (int) schedule.placedLessons().stream()
                .filter(p -> p.assignment().getId() == assignmentId && p.slot().dayOfWeek() == day)
                .count();
    }

    private String itemSummary(GreedyScheduler.AssignmentWorkItem item) {
        return "t=" + item.assignment().getTeacherId()
                + " s=" + item.assignment().getSubjectId()
                + " c=" + item.assignment().getClassStreamId()
                + " l=" + item.lessonIndex();
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
        final SchedulingContext context;

        DomainTracker(List<GreedyScheduler.AssignmentWorkItem> items, SchedulingContext context,
                      HardConstraintValidator hardValidator, Tier tier) {
            this.domains = new HashMap<>();
            this.byTeacher = new HashMap<>();
            this.byClass = new HashMap<>();
            this.degreeCache = new HashMap<>();
            this.assigned = new HashSet<>();
            this.history = new ArrayDeque<>();
            this.context = context;

            List<ScheduleSlot> allSlots = context.allSlots();
            int maxIdx = context.periodsPerDay();

            for (var item : items) {
                TeachingAssignment ta = item.assignment();
                Set<ScheduleSlot> valid = new HashSet<>();
                int limit = item.requiresConsecutive() ? maxIdx - 1 : maxIdx;
                for (ScheduleSlot slot : allSlots) {
                    int idx = context.indexOfLessonPeriod(slot.periodNumber());
                    if (idx < 0 || idx >= limit) continue;
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
                if (size == 0) {
                    TeachingAssignment ta = entry.getKey().assignment();
                    LOG.fine("Empty domain for teacher=" + ta.getTeacherId()
                            + " class=" + ta.getClassStreamId()
                            + " subject=" + ta.getSubjectId()
                            + " lessonIndex=" + entry.getKey().lessonIndex());
                    return null;
                }
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

            pruned.addAll(pruneSlotFromOthers(slot, teacherId, classId));
            if (placed.requiresConsecutive()) {
                ScheduleSlot next = context.nextLessonSlot(slot);
                if (next != null) {
                    pruned.addAll(pruneSlotFromOthers(next, teacherId, classId));
                }
            }

            history.push(pruned);
            return pruned;
        }

        private List<PrunedPair> pruneSlotFromOthers(ScheduleSlot slot, long teacherId, long classId) {
            List<PrunedPair> pruned = new ArrayList<>();
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

        void markSkipped(GreedyScheduler.AssignmentWorkItem item) {
            assigned.add(item);
            Set<ScheduleSlot> d = domains.get(item);
            if (d != null) d.clear();
            LOG.fine("Skipped item teacher=" + item.assignment().getTeacherId()
                    + " subject=" + item.assignment().getSubjectId()
                    + " lessonIndex=" + item.lessonIndex());
        }

        void pruneBySlotStatic(long teacherId, long classId, ScheduleSlot slot) {
            pruneSlotFromOthers(slot, teacherId, classId);
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
        if (count >= assignment.getLessonsPerWeek()) return false;
        return true;
    }

    private List<String> merge(List<String> a, List<String> b) {
        List<String> merged = new ArrayList<>(a);
        merged.addAll(b);
        return merged;
    }
}
