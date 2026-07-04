package com.thorium.domain.constraint;

import com.thorium.domain.model.LessonDuration;
import com.thorium.domain.model.ScheduleSlot;
import com.thorium.domain.model.TeachingAssignment;
import com.thorium.domain.scheduling.PlacedLesson;
import com.thorium.domain.scheduling.PartialSchedule;
import com.thorium.domain.scheduling.SchedulingContext;
import com.thorium.domain.value.DayOfWeek;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SoftConstraintScorer {

    private final double spreadWeight;
    private final double consecutiveWeight;
    private final double balanceWeight;

    public SoftConstraintScorer() {
        this(0.50, 0.40, 0.10);
    }

    public SoftConstraintScorer(double spreadWeight, double consecutiveWeight) {
        this(spreadWeight, consecutiveWeight, 0.10);
    }

    public SoftConstraintScorer(double spreadWeight, double consecutiveWeight, double balanceWeight) {
        double total = spreadWeight + consecutiveWeight + balanceWeight;
        this.spreadWeight = spreadWeight / total;
        this.consecutiveWeight = consecutiveWeight / total;
        this.balanceWeight = balanceWeight / total;
    }

    public double score(PartialSchedule schedule, SchedulingContext context) {
        if (schedule.isEmpty()) {
            return 0.0;
        }
        double spreadScore = scoreSpread(schedule, context);
        double consecutiveScore = scoreConsecutiveLessons(schedule, context);
        double balanceScore = scoreTeacherBalance(schedule, context);
        return spreadScore * spreadWeight + consecutiveScore * consecutiveWeight + balanceScore * balanceWeight;
    }

    public double scorePlacement(TeachingAssignment assignment, ScheduleSlot slot,
                                  PartialSchedule schedule, SchedulingContext context) {
        PartialSchedule trial = schedule.copy();
        trial.place(new PlacedLesson(assignment, slot));
        return score(trial, context);
    }

    private double scoreTeacherBalance(PartialSchedule schedule, SchedulingContext context) {
        Map<Long, Map<DayOfWeek, Integer>> teacherDailyLoad = new HashMap<>();
        for (PlacedLesson placed : schedule.placedLessons()) {
            teacherDailyLoad
                    .computeIfAbsent(placed.assignment().getTeacherId(), k -> new EnumMap<>(DayOfWeek.class))
                    .merge(placed.slot().dayOfWeek(), 1, Integer::sum);
        }
        if (teacherDailyLoad.isEmpty()) return 1.0;
        double totalDeviation = 0.0;
        int teacherCount = 0;
        for (var entry : teacherDailyLoad.entrySet()) {
            var dayCounts = entry.getValue();
            if (dayCounts.size() <= 1) continue;
            int min = dayCounts.values().stream().mapToInt(Integer::intValue).min().orElse(0);
            int max = dayCounts.values().stream().mapToInt(Integer::intValue).max().orElse(0);
            int range = max - min;
            int days = context.workingDays().size();
            totalDeviation += (double) range / Math.max(1, days);
            teacherCount++;
        }
        return teacherCount > 0 ? 1.0 - Math.min(1.0, totalDeviation / teacherCount) : 1.0;
    }

    private double scoreSpread(PartialSchedule schedule, SchedulingContext context) {
        Map<Long, Map<DayOfWeek, Integer>> perAssignmentDayCount = new HashMap<>();
        Map<Long, Integer> assignmentTotalLessons = new HashMap<>();
        Map<Long, Boolean> assignmentIsDouble = new HashMap<>();
        for (PlacedLesson placed : schedule.placedLessons()) {
            perAssignmentDayCount
                    .computeIfAbsent(placed.assignment().getId(), k -> new EnumMap<>(DayOfWeek.class))
                    .merge(placed.slot().dayOfWeek(), 1, Integer::sum);
            assignmentTotalLessons.merge(placed.assignment().getId(), 1, Integer::sum);
            if (placed.assignment().getDuration() == LessonDuration.DOUBLE) {
                assignmentIsDouble.put(placed.assignment().getId(), true);
            }
        }

        int workingDays = context.workingDays().size();

        double total = 0.0;
        int groups = 0;
        for (var entry : perAssignmentDayCount.entrySet()) {
            Map<DayOfWeek, Integer> dayCounts = entry.getValue();
            int totalLpW = assignmentTotalLessons.getOrDefault(entry.getKey(), 0);
            boolean isDouble = assignmentIsDouble.getOrDefault(entry.getKey(), false);

            int divisor = isDouble ? 2 : 1;
            int adjustedTotal = (int) Math.ceil((double) totalLpW / divisor);
            int actualMaxOnDay = dayCounts.values().stream().mapToInt(Integer::intValue).max().orElse(0);
            int adjustedMax = (int) Math.ceil((double) actualMaxOnDay / divisor);
            int distinctDays = dayCounts.size();

            int idealMaxOnDay = (int) Math.ceil((double) adjustedTotal / Math.max(1, workingDays));
            int idealDistinctDays = Math.min(adjustedTotal, workingDays);

            int deviation = Math.max(0, adjustedMax - idealMaxOnDay);
            double spreadPenalty = Math.pow(deviation, 1.5) / Math.max(1, idealMaxOnDay);
            double maxScore = Math.max(0.0, 1.0 - spreadPenalty);

            double dayCoverage = (double) distinctDays / Math.max(1, idealDistinctDays);

            total += (maxScore * 0.7 + dayCoverage * 0.3);
            groups++;
        }
        return groups == 0 ? 0.0 : total / groups;
    }

    private double scoreConsecutiveLessons(PartialSchedule schedule, SchedulingContext context) {
        Map<Long, Map<DayOfWeek, List<Integer>>> assignmentPeriods = new HashMap<>();
        Map<Long, TeachingAssignment> assignmentMap = new HashMap<>();
        for (PlacedLesson placed : schedule.placedLessons()) {
            assignmentPeriods
                    .computeIfAbsent(placed.assignment().getId(), k -> new EnumMap<>(DayOfWeek.class))
                    .computeIfAbsent(placed.slot().dayOfWeek(), k -> new java.util.ArrayList<>())
                    .add(placed.slot().periodNumber());
            assignmentMap.putIfAbsent(placed.assignment().getId(), placed.assignment());
        }

        int penalties = 0;
        int total = 0;
        for (var entry : assignmentPeriods.entrySet()) {
            TeachingAssignment ta = assignmentMap.get(entry.getKey());
            boolean allowsDouble = ta != null && context.subject(ta.getSubjectId())
                    .map(s -> s.isAllowsDoublePeriod() || s.isRequiresDoublePeriod())
                    .orElse(false);
            for (List<Integer> periods : entry.getValue().values()) {
                periods.sort(Integer::compareTo);
                for (int i = 1; i < periods.size(); i++) {
                    total++;
                    boolean isConsecutive = periods.get(i) - periods.get(i - 1) == 1;
                    if (isConsecutive && !allowsDouble) {
                        penalties++;
                    }
                }
            }
        }
        if (total == 0) {
            return 1.0;
        }
        return 1.0 - ((double) penalties / total);
    }
}
