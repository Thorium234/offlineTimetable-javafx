# Timeline Engine Refactor — Implementation Plan

## Overview

Convert the timetable system to match a real school schedule (07:10-18:45, 10 periods), with Assembly as a lesson-assignable slot (period 1), all other breaks as non-teaching dividers, and `isBeforePeriodOne` breaks starting AT school start time (not before it).

## Phase 1 — Fix Timeline Algorithm

### 1a. `PeriodConfigurationUseCase.recalculateMasterTimeline()`
- **Fix**: `isBeforePeriodOne` break starts at `clockCursor` (not `cursor - duration`), then `clockCursor += duration`.
- **Add**: For `slotable` `isBeforePeriodOne` breaks, also create a `Period` entry in the DB (period_number = 1 for Assembly).
- **Add**: For `slotable` non-`isBeforePeriodOne` breaks, also create a `Period` entry with the next sequential period_number.

### 1b. `DailyTimelineGenerator.generate()`
- **Fix**: Check `breaks` for `isBeforePeriodOne=true` entries. If found, prepend them to the timeline at the beginning (before any period blocks).
- The `schoolStartTimeOverride` variant should handle cursor correctly.

### 1c. `PeriodConfigurationController.autoComputeTimes()`
- **Fix**: Before computing period times, advance cursor past any `isBeforePeriodOne` break.

### 1d. `BreakConfigurationController.onGenerateDefaults()`
- **Fix**: Short Break at afterPeriod=5 (not 4), Games Time duration=165 (not 40).
- **Add**: Set `slotable=true` for Assembly, `slotable=false` for Tea/Short/Lunch/Games.

## Phase 2 — Data Layer (V9 Migration)

### 2a. DatabaseInitializer.java
- **SCHEMA_VERSION** → 9
- **V9 migrations**:
  - `ALTER TABLE timetable_entries ADD COLUMN slot_type TEXT NOT NULL DEFAULT 'PERIOD' CHECK (slot_type IN ('PERIOD','BREAK'))`
  - `ALTER TABLE timetable_entries ADD COLUMN break_id INTEGER REFERENCES breaks(id) ON DELETE SET NULL`
  - `ALTER TABLE breaks ADD COLUMN slotable INTEGER NOT NULL DEFAULT 0`
  - `UPDATE timetable_entries SET period_number = period_number + 1` (shift by +1 for Assembly)
  - `UPDATE teacher_availability SET period_number = period_number + 1`
- **Seed defaults**:
  - school_settings: 10 periods, 07:10 start, 18:45 end, 40 min duration
  - Breaks: Assembly(50min, afterPeriod=0, isBeforeP1=true, slotable=true, 07:10-08:00)
           Tea(20min, afterPeriod=3, slotable=false, 10:00-10:20)
           Short(10min, afterPeriod=5, slotable=false, 11:40-11:50)
           Lunch(50min, afterPeriod=7, slotable=false, 13:10-14:00)
           Games(165min, afterPeriod=10, slotable=false, 16:00-18:45)
  - Periods: period 1=Assembly 07:10-08:00, P1=period2 08:00-08:40, ..., P10=period11 15:20-16:00

### 2b. SqliteBreakRepository.java
- Add `slotable` to insert/update SQL and bind params.
- Map `slotable` column in `map()` method.

### 2c. SqliteTimetableRepository.java
- Add `slot_type` and `break_id` to insert/update SQL for entries.
- Add `slot_type` and `break_id` to saveWithEntries() SQL.
- Map `slot_type` and `break_id` in `mapEntry()`.

## Phase 3 — Scheduling Engine

### 3a. SchedulingContext.java
- No change needed to the class itself — `periodsPerDay` is set by the caller.
- `periodRepository.count()` now returns 11 (Assembly + P1-P10).
- `allSlots()` iterates `period = 1` to `periodsPerDay` which covers all slots.

### 3b. GenerateTimetableUseCase.java
- `periodsPerDay = periodRepository.count()` — automatically 11 now.
- The generator fills slots 1-11. Slot 1 = Assembly, slots 2-11 = P1-P10.
- Entries saved with `slotType = "PERIOD"` and `breakId = null` by default.

## Phase 4 — Timetable Editor

### 4a. TimetableEditorUseCase.java
- `loadState()`: `periods` already loads from `periodRepository.findAll()` — includes Assembly as period 1.
- `validatePlacement()`: already works with period numbers 1-11.
- `placeLesson()` / `moveEntry()`: already works with the expanded period range.

## Phase 5 — UI Viewer

### 5a. TimetableViewerController.java
- `renderGrid()`: Build a combined slot list from periods + breaks:
  1. Add all PeriodDto items sorted by periodNumber (1-11).
  2. For each period, check if a NON-slotable break exists with `afterPeriod == periodNumber - 1` (since periodNumber is shifted by +1).
  3. Actually: `afterPeriod` on breaks refers to the ORIGINAL period number (1-10 for P1-P10). So Tea after P3 = afterPeriod=3, which in the new scheme corresponds to period number 4 (= P3). So `break.afterPeriod == periodNumber - 1` for the first period, `break.afterPeriod == periodNumber - 1` for all periods.
  
  Actually simpler approach: iterate slots in time order:
  - Slot 1 (period 1, Assembly) → column
  - Slot 2 (period 2, P1) → column. Check if break with afterPeriod == originalPeriod(1) exists. Tea has afterPeriod=3 not 1. So no break between Assembly and P1.
  - Slot 3 (period 3, P2) → column
  - Slot 4 (period 4, P3) → column. Check afterPeriod == 3 → Tea Break (full-width divider)
  - Slot 5 (period 5, P4) → column
  - Slot 6 (period 6, P5) → column. Check afterPeriod == 5 → Short Break (full-width divider)
  - ...
  
  So for each period p (shifted, 1-11), the original period number `origP = p - 1` (for p>=2). For p=1 (Assembly), no break comes after it (the next slot is P1 at period 2).

Actually, let me think more carefully:

After the +1 shift:
- Period 1 in table = Assembly (not a regular period)
- Period 2 in table = P1 (the first regular period)
- Period 3 in table = P2
- Period 4 in table = P3
- Period 5 in table = P4
- Period 6 in table = P5
- Period 7 in table = P6
- Period 8 in table = P7
- Period 9 in table = P8
- Period 10 in table = P9
- Period 11 in table = P10

Breaks' `afterPeriod` values remain at their ORIGINAL values (referring to P1-P10 before shift):
- Tea after P3 = afterPeriod=3 (which is period 4 in the new scheme)
- Short after P5 = afterPeriod=5 (period 6)
- Lunch after P7 = afterPeriod=7 (period 8)
- Games after P10 = afterPeriod=10 (period 11)

Assembly doesn't use `afterPeriod` for positioning — it uses `isBeforePeriodOne`.

For the viewer grid rendering:
```
for each period in periods (sorted by periodNumber):
    render period column
    int origPeriodNumber = period.periodNumber - 1  // map back to 1-10
    if break exists with afterPeriod == origPeriodNumber:
        render break divider after this column
```

For Assembly (periodNumber=1): origPeriodNumber=0, no break has afterPeriod=0, so no divider after Assembly. Correct!

For P1 (periodNumber=2): origPeriodNumber=1, no break has afterPeriod=1. Correct!

Wait, but in the reference timetable, after P1 there's no break. After P10 there's Games (afterPeriod=10, which maps to periodNumber=11). So Games appears after P10 column. Correct!

This logic works.

### 5b. Non-slotable break columns in viewer
- The viewer currently renders non-slotable breaks as full-width dividers.
- Keep this behavior for Tea/Short/Lunch/Games.
- For `slotable=true` breaks: render as a regular period column (like Assembly).
  - In the current implementation, slotable breaks ARE in the `periods` table, so they'll appear as regular period columns automatically.
  - No special handling needed!

Wait, this is a key realization: slotable breaks ARE in the periods table (created by `recalculateMasterTimeline`). So the viewer already sees them as regular periods. We DON'T need special rendering for slotable breaks — they're just periods with different labels/durations.

The only special rendering needed is for NON-slotable breaks — which are the current full-width dividers. This is already implemented.

So Phase 2/3 is actually simpler than I thought! The main changes are:

1. `recalculateMasterTimeline` creates Period entries for slotable breaks
2. The viewer treats all periods uniformly
3. Non-slotable breaks remain as dividers
4. The scheduling engine works with more periods (11 instead of 10)

## Phase 5 — PDF Exporter

### 5a. PdfTimetableExporter.java
- `DailyTimelineGenerator.generate()` must include the isBeforePeriodOne event block.
- The exporter iterates the timeline with both LessonBlocks and BreakBlocks/EventBlocks.
- Block for Assembly would come from a `Period` in the periods table → `LessonBlock`.
- Block for non-slotable breaks comes from `BreakPeriod` → `BreakBlock` / `EventBlock`.

## Phase 6 — Tests
- Verify timeline computation with 40 min periods and Assembly at 07:10-08:00.
- Verify Tea after original P3 (now period 4) at 10:00-10:20.
- Verify Short after original P5 (now period 6) at 11:40-11:50.
- Verify Lunch after original P7 (now period 8) at 13:10-14:00.
- Verify Games after original P10 (now period 11) at 16:00-18:45.

## Files to Modify (17 total)

### Domain (3):
- `BreakPeriod.java` — add `slotable` field
- `TimetableEntry.java` — add `slotType`, `breakId`
- `DailyTimelineGenerator.java` — prepend isBeforePeriodOne events

### Application (5):
- `BreakDto.java` — add `slotable`
- `EntityMapper.java` — map `slotable`
- `BreakConfigurationUseCase.java` — map `slotable` in toEntity
- `PeriodConfigurationUseCase.java` — fix algorithm, create Period for slotable breaks
- `GenerateTimetableUseCase.java` — no code change needed (periodRepository.count() returns 11)

### Infrastructure (3):
- `DatabaseInitializer.java` — V9 migration + new defaults
- `SqliteBreakRepository.java` — handle `slotable`
- `SqliteTimetableRepository.java` — handle `slot_type`, `break_id`

### UI (4):
- `PeriodConfigurationController.java` — fix autoComputeTimes
- `BreakConfigurationController.java` — fix Generate Defaults
- `TimetableViewerController.java` — map break afterPeriod to periodNumber-1
- `SettingsController.java` — no change needed (recalculateMasterTimeline already fixed)

### Export (1):
- `PdfTimetableExporter.java` — uses DailyTimelineGenerator, which is already fixed

### Config (1):
- `.opencode.jsonc` — add edit permissions
