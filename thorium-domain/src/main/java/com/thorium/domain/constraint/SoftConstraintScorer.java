package com.thorium.domain.constraint;

import com.thorium.domain.model.ScheduleSlot;
import com.thorium.domain.model.Teacher;
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

    private static final double SPREAD_WEIGHT = 0.35;
    private static final double WORKLOAD_WEIGHT = 0.40;
    private static final double CONSECUTIVE_WEIGHT = 0.25;

    public double score(PartialSchedule schedule, SchedulingContext context) {
        if (schedule.isEmpty()) {
            return 0.0;
        }
        double spreadScore = scoreSpread(schedule, context);
        double workloadScore = scoreTeacherWorkload(schedule, context);
        double consecutiveScore = scoreConsecutiveLessons(schedule, context);
        return spreadScore * SPREAD_WEIGHT + workloadScore * WORKLOAD_WEIGHT + consecutiveScore * CONSECUTIVE_WEIGHT;
    }

    public double scorePlacement(TeachingAssignment assignment, ScheduleSlot slot,
                                  PartialSchedule schedule, SchedulingContext context) {
        PartialSchedule trial = schedule.copy();
        trial.place(new PlacedLesson(assignment, slot));
        return score(trial, context);
    }

    private double scoreSpread(PartialSchedule schedule, SchedulingContext context) {
        Map<Long, Map<DayOfWeek, Integer>> perAssignmentDayCount = new HashMap<>();
        Map<Long, Integer> assignmentTotalLessons = new HashMap<>();
        for (PlacedLesson placed : schedule.placedLessons()) {
            perAssignmentDayCount
                    .computeIfAbsent(placed.assignment().getId(), k -> new EnumMap<>(DayOfWeek.class))
                    .merge(placed.slot().dayOfWeek(), 1, Integer::sum);
            assignmentTotalLessons.merge(placed.assignment().getId(), 1, Integer::sum);
        }

        int workingDays = context.workingDays().size();

        double total = 0.0;
        int groups = 0;
        for (var entry : perAssignmentDayCount.entrySet()) {
            Map<DayOfWeek, Integer> dayCounts = entry.getValue();
            int totalLpW = assignmentTotalLessons.getOrDefault(entry.getKey(), 0);

            int actualMaxOnDay = dayCounts.values().stream().mapToInt(Integer::intValue).max().orElse(0);
            int distinctDays = dayCounts.size();
            int idealMaxOnDay = (int) Math.ceil((double) totalLpW / Math.max(1, workingDays));
            int idealDistinctDays = Math.min(totalLpW, workingDays);

            int deviation = Math.max(0, actualMaxOnDay - idealMaxOnDay);
            double spreadPenalty = deviation / (double) Math.max(1, idealMaxOnDay);
            double maxScore = 1.0 - Math.min(1.0, spreadPenalty);

            double dayCoverage = (double) distinctDays / Math.max(1, idealDistinctDays);

            total += (maxScore * 0.6 + dayCoverage * 0.4);
            groups++;
        }
        return groups == 0 ? 0.0 : total / groups;
    }

    private double scoreTeacherWorkload(PartialSchedule schedule, SchedulingContext context) {
        Map<Long, Map<DayOfWeek, Integer>> teacherDayLessons = new HashMap<>();
        Map<Long, Integer> teacherTotalLessons = new HashMap<>();
        for (PlacedLesson placed : schedule.placedLessons()) {
            Long teacherId = placed.assignment().getTeacherId();
            teacherDayLessons
                    .computeIfAbsent(teacherId, k -> new EnumMap<>(DayOfWeek.class))
                    .merge(placed.slot().dayOfWeek(), 1, Integer::sum);
            teacherTotalLessons.merge(teacherId, 1, Integer::sum);
        }

        double total = 0.0;
        int teachers = 0;
        for (Map.Entry<Long, Map<DayOfWeek, Integer>> entry : teacherDayLessons.entrySet()) {
            Teacher teacher = context.teacher(entry.getKey()).orElse(null);
            if (teacher == null) {
                continue;
            }
            int maxDay = entry.getValue().values().stream().mapToInt(Integer::intValue).max().orElse(0);
            double dayRatio = 1.0 - ((double) maxDay / Math.max(1, teacher.getMaxLessonsPerDay()));
            double dayScore = Math.max(0.0, dayRatio);

            int weeklyTotal = teacherTotalLessons.getOrDefault(entry.getKey(), 0);
            double weekRatio = 1.0 - ((double) weeklyTotal / Math.max(1, teacher.getMaxLessonsPerWeek()));
            double weekScore = Math.max(0.0, weekRatio);

            total += (dayScore * 0.6 + weekScore * 0.4);
            teachers++;
        }
        return teachers == 0 ? 1.0 : total / teachers;
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
            boolean requiresDouble = context.subject(entry.getKey().longValue())
                    .map(s -> s.isRequiresDoublePeriod())
                    .orElse(false);
            for (List<Integer> periods : entry.getValue().values()) {
                periods.sort(Integer::compareTo);
                for (int i = 1; i < periods.size(); i++) {
                    total++;
                    boolean isConsecutive = periods.get(i) - periods.get(i - 1) == 1;
                    if (isConsecutive) {
                        if (!requiresDouble) {
                            penalties++;
                        }
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
