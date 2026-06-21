package com.thorium.domain.constraint;

import com.thorium.domain.model.ScheduleSlot;
import com.thorium.domain.model.Subject;
import com.thorium.domain.model.TeachingAssignment;
import com.thorium.domain.scheduling.PlacedLesson;
import com.thorium.domain.scheduling.PartialSchedule;
import com.thorium.domain.scheduling.SchedulingContext;
import com.thorium.domain.value.DayOfWeek;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class HardConstraintValidator {

    public boolean canPlace(TeachingAssignment assignment, ScheduleSlot slot,
                            PartialSchedule schedule, SchedulingContext context) {
        if (!isTeacherAvailable(assignment, slot, context)) {
            return false;
        }
        if (isTeacherBusy(assignment, slot, schedule)) {
            return false;
        }
        if (isClassBusy(assignment, slot, schedule)) {
            return false;
        }
        if (violatesCbcNoDouble(assignment, slot, schedule, context)) {
            return false;
        }
        if (violatesRequiredDouble(assignment, slot, schedule, context)) {
            return false;
        }
        if (exceedsWeeklyCount(assignment, schedule)) {
            return false;
        }
        return true;
    }

    public ValidationResult validateComplete(PartialSchedule schedule, SchedulingContext context) {
        List<String> errors = new java.util.ArrayList<>();

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

        Set<String> teacherSlots = new HashSet<>();
        Set<String> classSlots = new HashSet<>();
        for (PlacedLesson placed : schedule.placedLessons()) {
            String teacherKey = placed.assignment().getTeacherId() + "|" + placed.slot();
            if (!teacherSlots.add(teacherKey)) {
                errors.add("Teacher clash at " + placed.slot());
            }
            String classKey = placed.assignment().getClassStreamId() + "|" + placed.slot();
            if (!classSlots.add(classKey)) {
                errors.add("Class clash at " + placed.slot());
            }
        }

        return errors.isEmpty() ? ValidationResult.ok() : ValidationResult.fail(errors);
    }

    private boolean areLessonsPaired(PartialSchedule schedule, TeachingAssignment assignment, SchedulingContext context) {
        List<PlacedLesson> lessons = schedule.placedLessons().stream()
                .filter(p -> p.assignment().getId().equals(assignment.getId()))
                .toList();

        for (PlacedLesson lesson : lessons) {
            boolean hasPair = false;
            for (PlacedLesson other : lessons) {
                if (lesson == other) continue;
                if (lesson.slot().dayOfWeek() == other.slot().dayOfWeek()
                        && Math.abs(lesson.slot().periodNumber() - other.slot().periodNumber()) == 1) {
                    hasPair = true;
                    break;
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
        return schedule.placedLessons().stream()
                .anyMatch(p -> p.assignment().getTeacherId().equals(assignment.getTeacherId())
                        && p.slot().equals(slot));
    }

    private boolean isClassBusy(TeachingAssignment assignment, ScheduleSlot slot, PartialSchedule schedule) {
        return schedule.placedLessons().stream()
                .anyMatch(p -> p.assignment().getClassStreamId().equals(assignment.getClassStreamId())
                        && p.slot().equals(slot));
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
        int period = slot.periodNumber();

        for (PlacedLesson placed : schedule.placedLessons()) {
            if (!placed.assignment().getId().equals(assignment.getId())) {
                continue;
            }
            if (placed.slot().dayOfWeek() != day) {
                continue;
            }
            int otherPeriod = placed.slot().periodNumber();
            if (Math.abs(otherPeriod - period) == 1) {
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

        boolean hasAdjacent = false;
        for (PlacedLesson placed : schedule.placedLessons()) {
            if (!placed.assignment().getId().equals(assignment.getId())) {
                continue;
            }
            if (placed.slot().dayOfWeek() != slot.dayOfWeek()) {
                continue;
            }
            if (Math.abs(placed.slot().periodNumber() - slot.periodNumber()) == 1) {
                hasAdjacent = true;
                break;
            }
        }

        if (!hasAdjacent && placedCount > 0) {
            return true;
        }

        if (remaining == 1 && placedCount > 0 && !hasAdjacent) {
            return true;
        }

        return false;
    }

    private boolean exceedsWeeklyCount(TeachingAssignment assignment, PartialSchedule schedule) {
        long count = schedule.placedLessons().stream()
                .filter(p -> p.assignment().getId().equals(assignment.getId()))
                .count();
        return count >= assignment.getLessonsPerWeek();
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
