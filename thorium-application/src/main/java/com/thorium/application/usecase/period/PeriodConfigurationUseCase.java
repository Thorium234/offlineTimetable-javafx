package com.thorium.application.usecase.period;

import com.thorium.application.dto.BreakDto;
import com.thorium.application.dto.PeriodDto;
import com.thorium.application.dto.SchoolSettingsDto;
import com.thorium.application.mapper.EntityMapper;
import com.thorium.application.port.BreakRepository;
import com.thorium.application.port.PeriodRepository;
import com.thorium.domain.model.BreakPeriod;
import com.thorium.domain.model.Period;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.List;

public class PeriodConfigurationUseCase {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    private final PeriodRepository periodRepository;
    private final BreakRepository breakRepository;

    public PeriodConfigurationUseCase(PeriodRepository periodRepository, BreakRepository breakRepository) {
        this.periodRepository = periodRepository;
        this.breakRepository = breakRepository;
    }

    public PeriodDto create(PeriodDto dto) {
        Period period = toEntity(dto);
        return EntityMapper.toDto(periodRepository.save(period));
    }

    public PeriodDto update(PeriodDto dto) {
        if (dto.id() == null) {
            throw new IllegalArgumentException("Period id is required for update");
        }
        return EntityMapper.toDto(periodRepository.save(toEntity(dto)));
    }

    public void delete(Long id) {
        periodRepository.deleteById(id);
    }

    public List<PeriodDto> findAll() {
        return periodRepository.findAll().stream().map(EntityMapper::toDto).toList();
    }

    public int periodsPerDay() {
        return periodRepository.count();
    }

    public void recalculateMasterTimeline(SchoolSettingsDto settings) {
        for (PeriodDto p : findAll()) {
            delete(p.id());
        }

        List<BreakDto> breaks = breakRepository.findAll().stream()
                .map(EntityMapper::toDto)
                .sorted(Comparator.comparingInt(BreakDto::sortOrder))
                .toList();

        int numberOfPeriods = settings.totalPeriods();
        int periodDuration = settings.periodDurationMinutes();
        LocalTime schoolStart = parseTime(settings.startTime(), "schoolStartTime");
        LocalTime clockCursor = schoolStart;
        int periodCounter = 0;

        // Process isBeforePeriodOne breaks (slotable → Period entries; non-slotable → advance cursor)
        for (BreakDto b : breaks) {
            if (!b.isBeforePeriodOne()) continue;
            if (b.slotable()) {
                periodCounter++;
                Period period = new Period();
                period.setPeriodNumber(periodCounter);
                period.setStartTime(clockCursor);
                period.setEndTime(clockCursor.plusMinutes(b.durationMinutes()));
                period.setLabel(b.name());
                periodRepository.save(period);
            }
            saveBreakTimes(b, clockCursor, clockCursor.plusMinutes(b.durationMinutes()));
            clockCursor = clockCursor.plusMinutes(b.durationMinutes());
        }

        // Process regular periods P1-P10 (periodNumber shifted by slotable count)
        for (int p = 1; p <= numberOfPeriods; p++) {
            periodCounter++;
            LocalTime periodStart = clockCursor;
            LocalTime periodEnd = clockCursor.plusMinutes(periodDuration);
            Period period = new Period();
            period.setPeriodNumber(periodCounter);
            period.setStartTime(periodStart);
            period.setEndTime(periodEnd);
            period.setLabel("P" + p);
            periodRepository.save(period);
            clockCursor = periodEnd;

            for (BreakDto b : breaks) {
                if (!b.isBeforePeriodOne() && b.afterPeriod() == p) {
                    LocalTime breakEnd = clockCursor.plusMinutes(b.durationMinutes());
                    saveBreakTimes(b, clockCursor, breakEnd);
                    clockCursor = breakEnd;
                }
            }
        }
    }

    private void saveBreakTimes(BreakDto dto, LocalTime start, LocalTime end) {
        BreakPeriod bp = new BreakPeriod(dto.id(), dto.name(), dto.afterPeriod(), dto.durationMinutes(),
                dto.sortOrder(), dto.isBeforePeriodOne(), dto.slotable(), start, end);
        breakRepository.save(bp);
    }

    private Period toEntity(PeriodDto dto) {
        Period period = new Period();
        period.setId(dto.id());
        period.setPeriodNumber(dto.periodNumber());
        period.setStartTime(parseTime(dto.startTime(), "startTime"));
        period.setEndTime(parseTime(dto.endTime(), "endTime"));
        period.setLabel(dto.label());
        return period;
    }

    private LocalTime parseTime(String time, String fieldName) {
        try {
            return LocalTime.parse(time, TIME_FORMAT);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(
                    fieldName + " must be in HH:mm format (e.g., 08:30), got: " + time);
        }
    }
}
