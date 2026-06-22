package com.thorium.application.usecase.breaks;

import com.thorium.application.dto.BreakDto;
import com.thorium.application.mapper.EntityMapper;
import com.thorium.application.port.BreakRepository;
import com.thorium.domain.model.BreakPeriod;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

public class BreakConfigurationUseCase {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    private final BreakRepository breakRepository;

    public BreakConfigurationUseCase(BreakRepository breakRepository) {
        this.breakRepository = breakRepository;
    }

    public BreakDto create(BreakDto dto) {
        validate(dto);
        return EntityMapper.toDto(breakRepository.save(toEntity(dto)));
    }

    public BreakDto update(BreakDto dto) {
        if (dto.id() == null) {
            throw new IllegalArgumentException("Break id is required for update");
        }
        validate(dto);
        return EntityMapper.toDto(breakRepository.save(toEntity(dto)));
    }

    public void delete(Long id) {
        breakRepository.deleteById(id);
    }

    public List<BreakDto> findAll() {
        return breakRepository.findAll().stream().map(EntityMapper::toDto).toList();
    }

    private BreakPeriod toEntity(BreakDto dto) {
        BreakPeriod bp = new BreakPeriod();
        bp.setId(dto.id());
        bp.setName(dto.name());
        bp.setAfterPeriod(dto.afterPeriod());
        bp.setDurationMinutes(dto.durationMinutes());
        bp.setSortOrder(dto.sortOrder());
        bp.setBeforePeriodOne(dto.isBeforePeriodOne());
        bp.setSlotable(dto.slotable());
        if (dto.startTime() != null && !dto.startTime().isBlank()) {
            bp.setStartTime(parseTime(dto.startTime()));
        }
        if (dto.endTime() != null && !dto.endTime().isBlank()) {
            bp.setEndTime(parseTime(dto.endTime()));
        }
        return bp;
    }

    private void validate(BreakDto dto) {
        if (dto.name() == null || dto.name().isBlank()) {
            throw new IllegalArgumentException("Break name is required");
        }
        if (dto.durationMinutes() <= 0) {
            throw new IllegalArgumentException("Duration must be positive");
        }
    }

    private LocalTime parseTime(String time) {
        try {
            return LocalTime.parse(time, TIME_FORMAT);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Time must be in HH:mm format (e.g., 08:30), got: " + time);
        }
    }
}
