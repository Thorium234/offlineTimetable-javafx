package com.thorium.domain.scheduling;

import com.thorium.domain.model.ScheduleSlot;
import com.thorium.domain.model.TeachingAssignment;
import com.thorium.domain.value.DayOfWeek;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PartialScheduleTest {

    private final TeachingAssignment assignment = new TeachingAssignment(1L, 1L, 1L, 1L, 2);

    @Test
    void newScheduleIsEmpty() {
        PartialSchedule schedule = new PartialSchedule();
        assertTrue(schedule.isEmpty());
        assertEquals(0, schedule.size());
    }

    @Test
    void placeAddsLesson() {
        PartialSchedule schedule = new PartialSchedule();
        schedule.place(new PlacedLesson(assignment, new ScheduleSlot(DayOfWeek.MONDAY, 1)));
        assertEquals(1, schedule.size());
        assertFalse(schedule.isEmpty());
    }

    @Test
    void removeLastRemovesAndReturns() {
        PartialSchedule schedule = new PartialSchedule();
        PlacedLesson lesson = new PlacedLesson(assignment, new ScheduleSlot(DayOfWeek.MONDAY, 1));
        schedule.place(lesson);
        PlacedLesson removed = schedule.removeLast();
        assertEquals(lesson, removed);
        assertTrue(schedule.isEmpty());
    }

    @Test
    void removeLastOnEmptyThrows() {
        PartialSchedule schedule = new PartialSchedule();
        assertThrows(IllegalStateException.class, schedule::removeLast);
    }

    @Test
    void removeSpecificLesson() {
        PartialSchedule schedule = new PartialSchedule();
        PlacedLesson lesson1 = new PlacedLesson(assignment, new ScheduleSlot(DayOfWeek.MONDAY, 1));
        PlacedLesson lesson2 = new PlacedLesson(assignment, new ScheduleSlot(DayOfWeek.TUESDAY, 1));
        schedule.place(lesson1);
        schedule.place(lesson2);
        schedule.remove(lesson1);
        assertEquals(1, schedule.size());
        assertEquals(lesson2, schedule.placedLessons().getFirst());
    }

    @Test
    void copyIsIndependent() {
        PartialSchedule schedule = new PartialSchedule();
        schedule.place(new PlacedLesson(assignment, new ScheduleSlot(DayOfWeek.MONDAY, 1)));
        PartialSchedule copy = schedule.copy();
        copy.place(new PlacedLesson(assignment, new ScheduleSlot(DayOfWeek.TUESDAY, 1)));
        assertEquals(1, schedule.size());
        assertEquals(2, copy.size());
    }

    @Test
    void placedLessonsReturnsImmutableCopy() {
        PartialSchedule schedule = new PartialSchedule();
        schedule.place(new PlacedLesson(assignment, new ScheduleSlot(DayOfWeek.MONDAY, 1)));
        List<PlacedLesson> lessons = schedule.placedLessons();
        assertThrows(UnsupportedOperationException.class, () -> lessons.add(
                new PlacedLesson(assignment, new ScheduleSlot(DayOfWeek.TUESDAY, 1))));
    }

    @Test
    void constructingWithListCopies() {
        PlacedLesson lesson = new PlacedLesson(assignment, new ScheduleSlot(DayOfWeek.MONDAY, 1));
        List<PlacedLesson> original = new java.util.ArrayList<>(List.of(lesson));
        PartialSchedule schedule = new PartialSchedule(original);
        original.clear();
        assertEquals(1, schedule.size());
    }

    @Test
    void toEntriesGeneratesCorrectNumberOfEntries() {
        PartialSchedule schedule = new PartialSchedule();
        schedule.place(new PlacedLesson(assignment, new ScheduleSlot(DayOfWeek.MONDAY, 1)));
        schedule.place(new PlacedLesson(assignment, new ScheduleSlot(DayOfWeek.TUESDAY, 2)));
        var entries = schedule.toEntries(10L);
        assertEquals(2, entries.size());
        assertTrue(entries.stream().allMatch(e -> e.getTimetableId() == 10L));
    }
}
