package com.thorium.domain.scheduling;

import com.thorium.domain.model.BreakPeriod;
import com.thorium.domain.model.Period;
import com.thorium.domain.model.timeblock.BreakBlock;
import com.thorium.domain.model.timeblock.EventBlock;
import com.thorium.domain.model.timeblock.LessonBlock;
import com.thorium.domain.model.timeblock.TimeBlock;

import java.time.LocalTime;
import java.util.*;

public final class DailyTimelineGenerator {

    private DailyTimelineGenerator() {}

    public static List<TimeBlock> generate(List<Period> periods, List<BreakPeriod> breaks) {
        return generate(periods, breaks, List.of(), null);
    }

    public static List<TimeBlock> generate(List<Period> periods, List<BreakPeriod> breaks,
                                            List<EventBlock> prePeriodEvents, LocalTime schoolStartTimeOverride) {
        List<TimeBlock> timeline = new ArrayList<>();

        Map<Integer, Period> periodMap = new TreeMap<>();
        for (Period p : periods) {
            periodMap.put(p.getPeriodNumber(), p);
        }

        if (periodMap.isEmpty()) {
            return timeline;
        }

        int maxPeriod = periodMap.keySet().stream().mapToInt(Integer::intValue).max().orElse(0);

        List<BreakPeriod> sortedBreaks = breaks.stream()
                .sorted(Comparator.comparingInt(BreakPeriod::getSortOrder))
                .toList();

        Map<Integer, List<BreakPeriod>> breaksAfterPeriod = new HashMap<>();
        for (BreakPeriod b : sortedBreaks) {
            breaksAfterPeriod.computeIfAbsent(b.getAfterPeriod(), k -> new ArrayList<>()).add(b);
        }

        Map<Integer, List<EventBlock>> eventsBeforePeriod = new HashMap<>();
        for (EventBlock ev : prePeriodEvents) {
            eventsBeforePeriod.computeIfAbsent(1, k -> new ArrayList<>()).add(ev);
        }

        LocalTime cursor = schoolStartTimeOverride;
        boolean useCursor = (schoolStartTimeOverride != null);

        for (int pn = 1; pn <= maxPeriod; pn++) {
            if (!periodMap.containsKey(pn)) continue;

            Period period = periodMap.get(pn);

            if (useCursor) {
                int duration = (int) java.time.Duration.between(period.getStartTime(), period.getEndTime()).toMinutes();
                LessonBlock lesson = new LessonBlock(pn, cursor, cursor.plusMinutes(duration));
                timeline.add(lesson);
                cursor = lesson.endTime();
            } else {
                timeline.add(new LessonBlock(pn, period.getStartTime(), period.getEndTime()));
                cursor = period.getEndTime();
            }

            List<BreakPeriod> afterBreaks = breaksAfterPeriod.getOrDefault(pn, List.of());
            for (BreakPeriod b : afterBreaks) {
                LocalTime breakStart = cursor;
                LocalTime breakEnd = breakStart.plusMinutes(b.getDurationMinutes());
                timeline.add(new BreakBlock(b.getName(), breakStart, breakEnd, pn));
                cursor = breakEnd;
            }
        }

        return Collections.unmodifiableList(timeline);
    }
}
