package com.thorium.domain.scheduling;

import com.thorium.domain.model.TimetableEntry;

import java.util.ArrayList;
import java.util.List;

public final class PartialSchedule {

    private final List<PlacedLesson> placedLessons;

    public PartialSchedule() {
        this.placedLessons = new ArrayList<>();
    }

    public PartialSchedule(List<PlacedLesson> placedLessons) {
        this.placedLessons = new ArrayList<>(placedLessons);
    }

    public List<PlacedLesson> placedLessons() {
        return List.copyOf(placedLessons);
    }

    public void place(PlacedLesson lesson) {
        placedLessons.add(lesson);
    }

    public void remove(PlacedLesson lesson) {
        placedLessons.remove(lesson);
    }

    public PlacedLesson removeLast() {
        if (placedLessons.isEmpty()) {
            throw new IllegalStateException("No lessons to remove");
        }
        return placedLessons.removeLast();
    }

    public int size() {
        return placedLessons.size();
    }

    public boolean isEmpty() {
        return placedLessons.isEmpty();
    }

    public PartialSchedule copy() {
        return new PartialSchedule(placedLessons);
    }

    public List<TimetableEntry> toEntries(Long timetableId) {
        List<TimetableEntry> entries = new ArrayList<>();
        for (PlacedLesson placed : placedLessons) {
            TimetableEntry entry = new TimetableEntry();
            entry.setTimetableId(timetableId);
            entry.setTeachingAssignmentId(placed.assignment().getId());
            entry.setDayOfWeek(placed.slot().dayOfWeek());
            entry.setPeriodNumber(placed.slot().periodNumber());
            entries.add(entry);
        }
        return entries;
    }
}
