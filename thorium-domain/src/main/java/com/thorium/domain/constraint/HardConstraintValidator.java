package com.thorium.domain.constraint;

import com.thorium.domain.model.*;
import com.thorium.domain.scheduling.PlacedLesson;
import com.thorium.domain.scheduling.PartialSchedule;
import com.thorium.domain.scheduling.SchedulingContext;
import com.thorium.domain.value.DayOfWeek;

import java.util.*;

public class HardConstraintValidator {

    public boolean canPlace(TeachingAssignment assignment, ScheduleSlot slot,
                            PartialSchedule schedule, SchedulingContext context) {
        return canPlace(assignment, slot, schedule, context, null);
    }

    public boolean canPlace(TeachingAssignment assignment, ScheduleSlot slot,
                            PartialSchedule schedule, SchedulingContext context, Long excludeEntryId) {
        if (!isTeacherAvailable(assignment, slot, context)) {
            return false;
        }
        if (isTeacherBusy(assignment, slot, schedule, excludeEntryId)) {
            return false;
        }
        if (isClassBusy(assignment, slot, schedule, excludeEntryId)) {
            return false;
        }
        if (violatesCbcNoDouble(assignment, slot, schedule, context)) {
            return false;
        }
        if (violatesRequiredDouble(assignment, slot, schedule, context)) {
            return false;
        }
        if (exceedsWeeklyCount(assignment, schedule, excludeEntryId)) {
            return false;
        }
        if (exceedsTeacherDailyLimit(assignment, slot, schedule, context, excludeEntryId)) {
            return false;
        }
        if (exceedsTeacherWeeklyLimit(assignment, schedule, context, excludeEntryId)) {
            return false;
        }
        return true;
    }

    public String canPlaceReason(TeachingAssignment assignment, ScheduleSlot slot,
                                  PartialSchedule schedule, SchedulingContext context) {
        if (!isTeacherAvailable(assignment, slot, context)) {
            return "Teacher unavailable (teacherId=" + assignment.getTeacherId() + ")";
        }
        if (isTeacherBusy(assignment, slot, schedule)) {
            return "Teacher busy";
        }
        if (isClassBusy(assignment, slot, schedule)) {
            return "Class busy";
        }
        if (violatesCbcNoDouble(assignment, slot, schedule, context)) {
            return "CBC no double";
        }
        if (violatesRequiredDouble(assignment, slot, schedule, context)) {
            return "Required double violation";
        }
        if (exceedsWeeklyCount(assignment, schedule)) {
            return "Exceeds weekly count";
        }
        if (exceedsTeacherDailyLimit(assignment, slot, schedule, context, null)) {
            return "Exceeds teacher daily limit";
        }
        if (exceedsTeacherWeeklyLimit(assignment, schedule, context, null)) {
            return "Exceeds teacher weekly limit";
        }
        return null;
    }

    public boolean isRoomAvailable(Long roomId, ScheduleSlot slot, List<TimetableEntry> entries, Long excludeEntryId) {
        if (roomId == null) {
            return true;
        }
        for (TimetableEntry entry : entries) {
            if (excludeEntryId != null && excludeEntryId.equals(entry.getId())) {
                continue;
            }
            if (roomId.equals(entry.getRoomId())
                    && entry.getDayOfWeek() == slot.dayOfWeek()
                    && entry.getPeriodNumber() == slot.periodNumber()) {
                return false;
            }
        }
        return true;
    }

    public ValidationResult validateComplete(PartialSchedule schedule, SchedulingContext context) {
        List<String> errors = new ArrayList<>();

        Map<Long, Integer> assignmentCounts = new HashMap<>();
        for (PlacedLesson placed : schedule.placedLessons()) {
            assignmentCounts.merge(placed.assignment().getId(), 1, Integer::sum);
        }

        for (TeachingAssignment assignment : context.assignments()) {
            int count = assignmentCounts.getOrDefault(assignment.getId(), 0);
            if (count != assignment.getLessonsPerWeek()) {
                errors.add("Assignment " + assignment.getId() + " has " + count
                        + " lessons, expected " + assignment.getLessonsPerWeek());
            }

            Subject subject = context.subject(assignment.getSubjectId()).orElse(null);
            if (subject != null && subject.isRequiresDoublePeriod()) {
                if (count % 2 != 0) {
                    errors.add("Assignment " + assignment.getId() + " has odd lesson count ("
                            + count + ") but subject requires double periods");
                }
                if (!areLessonsPaired(schedule, assignment, context)) {
                    errors.add("Assignment " + assignment.getId() + " has lessons not placed in consecutive pairs");
                }
            }
        }

        var teacherSlotMap = buildTeacherSlotIndex(schedule);
        var classSlotMap = buildClassSlotIndex(schedule);
        for (var entry : teacherSlotMap.entrySet()) {
            if (entry.getValue().size() > 1) {
                errors.add("Teacher clash at " + entry.getKey());
            }
        }
        for (var entry : classSlotMap.entrySet()) {
            if (entry.getValue().size() > 1) {
                errors.add("Class clash at " + entry.getKey());
            }
        }

        return errors.isEmpty() ? ValidationResult.ok() : ValidationResult.fail(errors);
    }

    public Map<String, List<PlacedLesson>> buildTeacherSlotIndex(PartialSchedule schedule) {
        Map<String, List<PlacedLesson>> index = new HashMap<>();
        for (PlacedLesson placed : schedule.placedLessons()) {
            String key = placed.assignment().getTeacherId() + "|" + placed.slot();
            index.computeIfAbsent(key, k -> new ArrayList<>()).add(placed);
        }
        return index;
    }

    public Map<String, List<PlacedLesson>> buildClassSlotIndex(PartialSchedule schedule) {
        Map<String, List<PlacedLesson>> index = new HashMap<>();
        for (PlacedLesson placed : schedule.placedLessons()) {
            String key = placed.assignment().getClassStreamId() + "|" + placed.slot();
            index.computeIfAbsent(key, k -> new ArrayList<>()).add(placed);
        }
        return index;
    }

    public boolean isTeacherBusyFast(long teacherId, ScheduleSlot slot,
                                      Map<String, List<PlacedLesson>> teacherIndex) {
        String key = teacherId + "|" + slot;
        List<PlacedLesson> atSlot = teacherIndex.get(key);
        return atSlot != null && !atSlot.isEmpty();
    }

    public boolean isClassBusyFast(long classStreamId, ScheduleSlot slot,
                                    Map<String, List<PlacedLesson>> classIndex) {
        String key = classStreamId + "|" + slot;
        List<PlacedLesson> atSlot = classIndex.get(key);
        return atSlot != null && !atSlot.isEmpty();
    }

    private boolean areLessonsPaired(PartialSchedule schedule, TeachingAssignment assignment, SchedulingContext context) {
        List<PlacedLesson> lessons = schedule.placedLessons().stream()
                .filter(p -> p.assignment().getId().equals(assignment.getId()))
                .toList();

        for (PlacedLesson lesson : lessons) {
            boolean hasPair = false;
            int lessonIdx = context.indexOfLessonPeriod(lesson.slot().periodNumber());
            if (lessonIdx < 0) continue;
            for (PlacedLesson other : lessons) {
                if (lesson.equals(other)) continue;
                if (lesson.slot().dayOfWeek() == other.slot().dayOfWeek()) {
                    int otherIdx = context.indexOfLessonPeriod(other.slot().periodNumber());
                    if (otherIdx >= 0 && Math.abs(lessonIdx - otherIdx) == 1) {
                        hasPair = true;
                        break;
                    }
                }
            }
            if (!hasPair) return false;
        }
        return true;
    }

    private boolean isTeacherAvailable(TeachingAssignment assignment, ScheduleSlot slot, SchedulingContext context) {
        return !context.isTeacherUnavailable(assignment.getTeacherId(), slot);
    }

    private boolean isTeacherBusy(TeachingAssignment assignment, ScheduleSlot slot, PartialSchedule schedule) {
        return isTeacherBusy(assignment, slot, schedule, null);
    }

    private boolean isTeacherBusy(TeachingAssignment assignment, ScheduleSlot slot,
                                  PartialSchedule schedule, Long excludeEntryId) {
        return schedule.placedLessons().stream()
                .anyMatch(p -> !matchesExcludedEntry(p, excludeEntryId)
                        && p.assignment().getTeacherId().equals(assignment.getTeacherId())
                        && p.slot().equals(slot));
    }

    private boolean isClassBusy(TeachingAssignment assignment, ScheduleSlot slot, PartialSchedule schedule) {
        return isClassBusy(assignment, slot, schedule, null);
    }

    private boolean isClassBusy(TeachingAssignment assignment, ScheduleSlot slot,
                                PartialSchedule schedule, Long excludeEntryId) {
        return schedule.placedLessons().stream()
                .anyMatch(p -> !matchesExcludedEntry(p, excludeEntryId)
                        && p.assignment().getClassStreamId().equals(assignment.getClassStreamId())
                        && p.slot().equals(slot));
    }

    private boolean matchesExcludedEntry(PlacedLesson placed, Long excludeEntryId) {
        return excludeEntryId != null && excludeEntryId.equals(placed.entryId());
    }

    private boolean violatesCbcNoDouble(TeachingAssignment assignment, ScheduleSlot slot,
                                          PartialSchedule schedule, SchedulingContext context) {
        if (!context.isCbcNoDoubleLessonEnabled()) {
            return false;
        }
        Subject subject = context.subject(assignment.getSubjectId()).orElse(null);
        if (subject == null || !subject.isCbcSubject()) {
            return false;
        }
        if (subject.isAllowsDoublePeriod() || subject.isRequiresDoublePeriod()) {
            return false;
        }
        DayOfWeek day = slot.dayOfWeek();
        int periodIdx = context.indexOfLessonPeriod(slot.periodNumber());
        if (periodIdx < 0) return false;

        for (PlacedLesson placed : schedule.placedLessons()) {
            if (!placed.assignment().getId().equals(assignment.getId())) {
                continue;
            }
            if (placed.slot().dayOfWeek() != day) {
                continue;
            }
            int otherIdx = context.indexOfLessonPeriod(placed.slot().periodNumber());
            if (otherIdx >= 0 && Math.abs(otherIdx - periodIdx) == 1) {
                return true;
            }
        }
        return false;
    }

    private boolean violatesRequiredDouble(TeachingAssignment assignment, ScheduleSlot slot,
                                            PartialSchedule schedule, SchedulingContext context) {
        Subject subject = context.subject(assignment.getSubjectId()).orElse(null);
        if (subject == null || !subject.isRequiresDoublePeriod()) {
            return false;
        }

        long placedCount = schedule.placedLessons().stream()
                .filter(p -> p.assignment().getId().equals(assignment.getId()))
                .count();

        long remaining = assignment.getLessonsPerWeek() - placedCount;
        if (remaining <= 0) {
            return false;
        }

        int slotIdx = context.indexOfLessonPeriod(slot.periodNumber());
        if (slotIdx < 0) return true;

        boolean hasAdjacent = false;
        for (PlacedLesson placed : schedule.placedLessons()) {
            if (!placed.assignment().getId().equals(assignment.getId())) {
                continue;
            }
            if (placed.slot().dayOfWeek() != slot.dayOfWeek()) {
                continue;
            }
            int placedIdx = context.indexOfLessonPeriod(placed.slot().periodNumber());
            if (placedIdx >= 0 && Math.abs(placedIdx - slotIdx) == 1) {
                hasAdjacent = true;
                break;
            }
        }

        if (remaining == 1 && !hasAdjacent) {
            return true;
        }

        return false;
    }

    private boolean exceedsWeeklyCount(TeachingAssignment assignment, PartialSchedule schedule) {
        return exceedsWeeklyCount(assignment, schedule, null);
    }

    private boolean exceedsWeeklyCount(TeachingAssignment assignment, PartialSchedule schedule, Long excludeEntryId) {
        long count = schedule.placedLessons().stream()
                .filter(p -> !matchesExcludedEntry(p, excludeEntryId))
                .filter(p -> p.assignment().getId().equals(assignment.getId()))
                .count();
        return count >= assignment.getLessonsPerWeek();
    }

    private boolean exceedsTeacherDailyLimit(TeachingAssignment assignment, ScheduleSlot slot,
                                              PartialSchedule schedule, SchedulingContext context,
                                              Long excludeEntryId) {
        Teacher teacher = context.teacher(assignment.getTeacherId()).orElse(null);
        if (teacher == null) return false;
        int maxPerDay = teacher.getMaxLessonsPerDay();
        if (maxPerDay <= 0) return false;
        long dayCount = schedule.placedLessons().stream()
                .filter(p -> !matchesExcludedEntry(p, excludeEntryId))
                .filter(p -> p.assignment().getTeacherId().equals(assignment.getTeacherId()))
                .filter(p -> p.slot().dayOfWeek() == slot.dayOfWeek())
                .count();
        return dayCount >= maxPerDay;
    }

    private boolean exceedsTeacherWeeklyLimit(TeachingAssignment assignment, PartialSchedule schedule,
                                               SchedulingContext context, Long excludeEntryId) {
        Teacher teacher = context.teacher(assignment.getTeacherId()).orElse(null);
        if (teacher == null) return false;
        int maxPerWeek = teacher.getMaxLessonsPerWeek();
        if (maxPerWeek <= 0) return false;
        long weekCount = schedule.placedLessons().stream()
                .filter(p -> !matchesExcludedEntry(p, excludeEntryId))
                .filter(p -> p.assignment().getTeacherId().equals(assignment.getTeacherId()))
                .count();
        return weekCount >= maxPerWeek;
    }

    public record ValidationResult(boolean isValid, List<String> errors) {
        public static ValidationResult ok() {
            return new ValidationResult(true, List.of());
        }

        public static ValidationResult fail(List<String> errors) {
            return new ValidationResult(false, errors);
        }
    }
}
