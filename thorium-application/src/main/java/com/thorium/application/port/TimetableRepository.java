package com.thorium.application.port;

import com.thorium.domain.model.Timetable;
import com.thorium.domain.model.TimetableEntry;

import java.util.List;
import java.util.Optional;

public interface TimetableRepository {

    Timetable save(Timetable timetable);

    default void saveWithNameCheck(Timetable timetable) {
        if (findByName(timetable.getName()).isPresent()) {
            throw new IllegalArgumentException("Timetable with name '" + timetable.getName() + "' already exists");
        }
        save(timetable);
    }

    Timetable saveWithEntries(Timetable timetable, List<TimetableEntry> entries);

    default Timetable saveWithEntriesAndCheck(Timetable timetable, List<TimetableEntry> entries) {
        if (findByName(timetable.getName()).isPresent()) {
            throw new IllegalArgumentException("Timetable with name '" + timetable.getName() + "' already exists");
        }
        return saveWithEntries(timetable, entries);
    }

    Optional<Timetable> findById(Long id);

    Optional<TimetableWithEntries> findByIdWithEntries(Long id);

    Optional<Timetable> findByName(String name);

    List<Timetable> findAll();

    void deleteById(Long id);

    void deleteEntry(Long entryId);

    TimetableEntry saveEntry(TimetableEntry entry);

    record TimetableWithEntries(Timetable timetable, List<TimetableEntry> entries) {
    }
}
