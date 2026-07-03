package com.thorium.domain.scheduling;

import com.thorium.domain.constraint.HardConstraintValidator;
import com.thorium.domain.constraint.SoftConstraintScorer;
import com.thorium.domain.exception.SchedulingException;
import com.thorium.domain.model.LessonDuration;
import com.thorium.domain.model.ScheduleSlot;
import com.thorium.domain.model.TeachingAssignment;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

public class GreedyScheduler {

    private static final Logger LOG = Logger.getLogger(GreedyScheduler.class.getName());

    private final HardConstraintValidator hardValidator;
    private final SoftConstraintScorer softScorer;
    private final Random random;

    public GreedyScheduler(HardConstraintValidator hardValidator, SoftConstraintScorer softScorer) {
        this.hardValidator = hardValidator;
        this.softScorer = softScorer;
        this.random = new Random();
    }

    public PartialSchedule schedule(SchedulingContext context) {
        return schedule(context, null);
    }

    public PartialSchedule schedule(SchedulingContext context, GenerationProgressCallback callback) {
        PartialSchedule schedule = new PartialSchedule();
        List<AssignmentWorkItem> workItems = expandAssignments(context);
        workItems.sort(Comparator.comparingInt(AssignmentWorkItem::difficulty).reversed());

        int required = context.assignments().stream().mapToInt(a -> a.getLessonsPerWeek()).sum();
        int rejectedCount = 0;
        for (int i = 0; i < workItems.size(); i++) {
            if (callback != null && callback.isCancelled()) {
                LOG.info("Greedy scheduler cancelled at item " + i + "/" + workItems.size());
                if (callback != null) callback.log("INFO", "Greedy scheduler cancelled");
                return schedule;
            }
            AssignmentWorkItem item = workItems.get(i);
            ScheduleSlot bestSlot = findBestSlot(item.assignment(), item.requiresConsecutive, schedule, context);
            if (bestSlot != null) {
                schedule.place(new PlacedLesson(item.assignment(), bestSlot));
                if (item.requiresConsecutive) {
                    ScheduleSlot second = context.nextLessonSlot(bestSlot);
                    schedule.place(new PlacedLesson(item.assignment(), second));
                }
                if (callback != null) {
                    callback.progress(schedule.size(), required);
                }
            } else {
                rejectedCount++;
                String summary = "teacher=" + item.assignment().getTeacherId()
                        + " subject=" + item.assignment().getSubjectId()
                        + " class=" + item.assignment().getClassStreamId()
                        + " lessonIndex=" + item.lessonIndex();
                if (callback != null) {
                    callback.itemRejected(summary, "no valid slot found");
                }
                if (rejectedCount == 1) {
                    LOG.warning("No slot found for first item (" + summary
                            + ", lessons=" + item.assignment().getLessonsPerWeek()
                            + ") difficulty=" + item.difficulty);
                    logSlotDiagnostics(item.assignment(), schedule, context, callback);
                } else if (rejectedCount <= 5 || i == workItems.size() - 1) {
                    LOG.warning("No slot for assignment " + item.assignment().getId()
                            + " (" + summary + ") after " + schedule.size() + " placed");
                }
            }
        }
        if (rejectedCount > 0) {
            String msg = "Greedy scheduler: " + rejectedCount + "/" + workItems.size()
                    + " items could not be placed (" + schedule.size() + " placed)";
            LOG.warning(msg);
            if (callback != null) callback.log("WARN", msg);
            if (rejectedCount == workItems.size()) {
                throw new SchedulingException(msg);
            }
        }
        if (callback != null) callback.progress(schedule.size(), required);
        return schedule;
    }

    private void logSlotDiagnostics(TeachingAssignment assignment, PartialSchedule schedule, SchedulingContext context,
                                     GenerationProgressCallback callback) {
        String msg = "Diagnosing all " + context.allSlots().size() + " slots for assignment " + assignment.getId();
        LOG.warning(msg);
        if (callback != null) callback.log("INFO", msg);
        int rejected = 0;
        for (ScheduleSlot slot : context.allSlots()) {
            String reason = hardValidator.canPlaceReason(assignment, slot, schedule, context);
            if (reason != null) {
                rejected++;
                String line = "  Slot " + slot + " rejected: " + reason;
                if (rejected <= 5) {
                    LOG.warning(line);
                    if (callback != null) callback.log("WARN", line);
                }
            }
        }
        String summary = "Diagnostic: " + rejected + "/" + context.allSlots().size() + " slots rejected for first item";
        LOG.warning(summary);
        if (callback != null) callback.log("WARN", summary);
    }

    public List<AssignmentWorkItem> expandAssignments(SchedulingContext context) {
        List<AssignmentWorkItem> items = new ArrayList<>();
        for (TeachingAssignment assignment : context.assignments()) {
            int difficulty = computeDifficulty(assignment, context);
            boolean isDouble = assignment.getDuration() == LessonDuration.DOUBLE;
            int count = isDouble ? assignment.getLessonsPerWeek() / 2 : assignment.getLessonsPerWeek();
            for (int i = 0; i < count; i++) {
                items.add(new AssignmentWorkItem(assignment, difficulty, i, isDouble));
            }
        }
        return items;
    }

    private int computeDifficulty(TeachingAssignment assignment, SchedulingContext context) {
        int difficulty = assignment.getLessonsPerWeek() * 10;
        long unavailableCount = 0;
        long totalSlots = 0;
        boolean isDouble = assignment.getDuration() == LessonDuration.DOUBLE;
        List<Integer> periods = context.lessonPeriodNumbers();
        int maxIdx = isDouble ? periods.size() - 1 : periods.size();
        for (var day : context.workingDays()) {
            for (int idx = 0; idx < maxIdx; idx++) {
                int pn = periods.get(idx);
                totalSlots++;
                if (context.isTeacherUnavailable(assignment.getTeacherId(), new ScheduleSlot(day, pn))) {
                    unavailableCount++;
                }
                if (isDouble) {
                    int nextPn = periods.get(idx + 1);
                    if (context.isTeacherUnavailable(assignment.getTeacherId(), new ScheduleSlot(day, nextPn))) {
                        unavailableCount++;
                    }
                }
            }
        }
        if (totalSlots > 0) {
            difficulty += (int) ((unavailableCount * 100) / totalSlots);
        }

        int teacherLoad = 0;
        for (var a : context.assignments()) {
            if (a.getTeacherId().equals(assignment.getTeacherId())) {
                teacherLoad += a.getLessonsPerWeek();
            }
        }
        if (totalSlots > 0) {
            difficulty += (int) ((long) teacherLoad * 5 / totalSlots);
        }
        return difficulty;
    }

    private ScheduleSlot findBestSlot(TeachingAssignment assignment, boolean requiresConsecutive,
                                       PartialSchedule schedule, SchedulingContext context) {
        ScheduleSlot best = null;
        double bestScore = Double.NEGATIVE_INFINITY;

        int maxIdx = context.periodsPerDay() - (requiresConsecutive ? 1 : 0);
        List<ScheduleSlot> allSlots = context.allSlots();
        double maxLookAheadPenalty = 0.0;

        for (ScheduleSlot slot : allSlots) {
            int idx = context.indexOfLessonPeriod(slot.periodNumber());
            if (idx < 0 || idx >= maxIdx) continue;
            if (!hardValidator.canPlace(assignment, slot, schedule, context)) {
                continue;
            }
            if (requiresConsecutive) {
                int nextPn = context.lessonPeriodNumbers().get(idx + 1);
                ScheduleSlot next = new ScheduleSlot(slot.dayOfWeek(), nextPn);
                if (!hardValidator.canPlace(assignment, next, schedule, context)) {
                    continue;
                }
            }

            double score = softScorer.scorePlacement(assignment, slot, schedule, context);

            double diversityBonus = computeDiversityBonus(assignment, slot, schedule);
            score += diversityBonus;

            double lookAheadPenalty = computeLookAheadPenalty(assignment, slot, context, schedule);
            score -= lookAheadPenalty * 0.1;
            if (lookAheadPenalty > maxLookAheadPenalty) {
                maxLookAheadPenalty = lookAheadPenalty;
            }

            double tieThreshold = 0.005;
            if (Math.abs(score - bestScore) < tieThreshold) {
                if (random.nextBoolean()) {
                    bestScore = score;
                    best = slot;
                }
            } else if (score > bestScore) {
                bestScore = score;
                best = slot;
            }
        }
        return best;
    }

    private double computeDiversityBonus(TeachingAssignment assignment, ScheduleSlot slot,
                                          PartialSchedule schedule) {
        long lessonsOnSameDay = schedule.placedLessons().stream()
                .filter(p -> p.assignment().getId().equals(assignment.getId()))
                .filter(p -> p.slot().dayOfWeek() == slot.dayOfWeek())
                .count();
        return -lessonsOnSameDay * 0.02;
    }

    private double computeLookAheadPenalty(TeachingAssignment assignment, ScheduleSlot slot,
                                            SchedulingContext context, PartialSchedule schedule) {
        Set<Long> placedIds = new HashSet<>();
        for (var p : schedule.placedLessons()) {
            placedIds.add(p.assignment().getId());
        }

        int constrainedCount = 0;
        for (var other : context.assignments()) {
            if (other.getId().equals(assignment.getId())) continue;
            if (placedIds.contains(other.getId())) continue;
            if (other.getTeacherId().equals(assignment.getTeacherId())
                    || other.getClassStreamId().equals(assignment.getClassStreamId())) {
                if (context.isTeacherUnavailable(other.getTeacherId(), slot)) continue;
                constrainedCount++;
            }
        }
        return constrainedCount;
    }

    public record AssignmentWorkItem(TeachingAssignment assignment, int difficulty, int lessonIndex, boolean requiresConsecutive) {
        public AssignmentWorkItem(TeachingAssignment assignment, int difficulty, int lessonIndex) {
            this(assignment, difficulty, lessonIndex, false);
        }
    }
}
