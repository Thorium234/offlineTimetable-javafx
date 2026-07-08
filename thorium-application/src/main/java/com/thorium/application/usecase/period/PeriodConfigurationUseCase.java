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
        Period saved = periodRepository.save(period);
        syncBreakTimes(saved);
        return EntityMapper.toDto(saved);
    }

    public PeriodDto update(PeriodDto dto) {
        if (dto.id() == null) {
            throw new IllegalArgumentException("Period id is required for update");
        }
        Period saved = periodRepository.save(toEntity(dto));
        syncBreakTimes(saved);
        return EntityMapper.toDto(saved);
    }

    public void delete(Long id) {
        periodRepository.deleteById(id);
    }

    public List<PeriodDto> findAll() {
        return periodRepository.findAll().stream().map(EntityMapper::toDto).toList();
    }

    public int periodsPerDay() {
        return (int) periodRepository.findAll().stream().filter(p -> Period.TYPE_LESSON.equals(p.getType())).count();
    }

    public void recalculateMasterTimeline(SchoolSettingsDto settings) {
        for (PeriodDto p : findAll()) {
            delete(p.id());
        }

        List<BreakDto> breaks = breakRepository.findAll().stream()
                .map(EntityMapper::toDto)
                .sorted(Comparator.comparingInt(BreakDto::sortOrder))
                .toList();

        int periodDuration = settings.periodDurationMinutes();
        LocalTime cursor = parseTime(settings.startTime(), "schoolStartTime");
        int periodCounter = 0;
        int lessonCounter = 0;

        for (BreakDto b : breaks) {
            if (b.isBeforePeriodOne()) {
                periodCounter++;
                Period period = new Period();
                period.setPeriodNumber(periodCounter);
                period.setStartTime(cursor);
                period.setEndTime(cursor.plusMinutes(b.durationMinutes()));
                period.setLabel(b.name());
                period.setType(Period.TYPE_BREAK);
                period.setBreakId(b.id());
                periodRepository.save(period);
                saveBreakTimes(b, cursor, cursor.plusMinutes(b.durationMinutes()));
                cursor = cursor.plusMinutes(b.durationMinutes());
            } else {
                while (lessonCounter < b.afterPeriod()) {
                    lessonCounter++;
                    periodCounter++;
                    Period period = new Period();
                    period.setPeriodNumber(periodCounter);
                    period.setStartTime(cursor);
                    period.setEndTime(cursor.plusMinutes(periodDuration));
                    period.setLabel("P" + lessonCounter);
                    period.setType(Period.TYPE_LESSON);
                    periodRepository.save(period);
                    cursor = cursor.plusMinutes(periodDuration);
                }

                periodCounter++;
                Period period = new Period();
                period.setPeriodNumber(periodCounter);
                period.setStartTime(cursor);
                period.setEndTime(cursor.plusMinutes(b.durationMinutes()));
                period.setLabel(b.name());
                period.setType(Period.TYPE_BREAK);
                period.setBreakId(b.id());
                periodRepository.save(period);
                saveBreakTimes(b, cursor, cursor.plusMinutes(b.durationMinutes()));
                cursor = cursor.plusMinutes(b.durationMinutes());
            }
        }

        int totalLessonSlots = settings.totalPeriods();
        while (lessonCounter < totalLessonSlots) {
            lessonCounter++;
            periodCounter++;
            Period period = new Period();
            period.setPeriodNumber(periodCounter);
            period.setStartTime(cursor);
            period.setEndTime(cursor.plusMinutes(periodDuration));
            period.setLabel("P" + lessonCounter);
            period.setType(Period.TYPE_LESSON);
            periodRepository.save(period);
            cursor = cursor.plusMinutes(periodDuration);
        }
    }

    private void syncBreakTimes(Period period) {
        if (!period.isBreak() || period.getBreakId() == null) return;
        breakRepository.findById(period.getBreakId()).ifPresent(b -> {
            b.setStartTime(period.getStartTime());
            b.setEndTime(period.getEndTime());
            breakRepository.save(b);
        });
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
        period.setType(dto.type());
        period.setBreakId(dto.breakId());
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
