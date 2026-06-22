package com.thorium.application.mapper;

import com.thorium.application.dto.*;
import com.thorium.domain.model.*;

import com.thorium.domain.value.SubjectColorPalette;

import java.time.format.DateTimeFormatter;

public final class EntityMapper {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    private EntityMapper() {
    }

    public static TeacherDto toDto(Teacher teacher) {
        return new TeacherDto(
                teacher.getId(),
                teacher.getCode(),
                teacher.getName(),
                teacher.getMaxLessonsPerDay(),
                teacher.getMaxLessonsPerWeek(),
                teacher.isActive()
        );
    }

    public static Teacher toEntity(TeacherDto dto) {
        Teacher teacher = new Teacher();
        teacher.setId(dto.id());
        teacher.setCode(dto.code());
        teacher.setName(dto.name());
        teacher.setMaxLessonsPerDay(dto.maxLessonsPerDay());
        teacher.setMaxLessonsPerWeek(dto.maxLessonsPerWeek());
        teacher.setActive(dto.active());
        return teacher;
    }

    public static SubjectDto toDto(Subject subject) {
        return new SubjectDto(
                subject.getId(),
                subject.getCode(),
                subject.getName(),
                subject.isExaminable(),
                subject.getCbcDefaultLessons(),
                subject.isAllowsDoublePeriod(),
                subject.isRequiresDoublePeriod(),
                SubjectColorPalette.resolveColor(subject.getId(), subject.getColor())
        );
    }

    public static Subject toEntity(SubjectDto dto) {
        Subject subject = new Subject();
        subject.setId(dto.id());
        subject.setCode(dto.code());
        subject.setName(dto.name());
        subject.setExaminable(dto.examinable());
        subject.setCbcDefaultLessons(dto.cbcDefaultLessons());
        subject.setAllowsDoublePeriod(dto.allowsDoublePeriod());
        subject.setRequiresDoublePeriod(dto.requiresDoublePeriod());
        subject.setColor(dto.color());
        return subject;
    }

    public static ClassStreamDto toDto(ClassStream cs) {
        return new ClassStreamDto(cs.getId(), cs.getCode(), cs.getForm(), cs.getStream(), cs.getDisplayName());
    }

    public static ClassStream toEntity(ClassStreamDto dto) {
        ClassStream cs = new ClassStream();
        cs.setId(dto.id());
        cs.setCode(dto.code());
        cs.setForm(dto.form());
        cs.setStream(dto.stream());
        cs.setDisplayName(dto.displayName());
        return cs;
    }

    public static PeriodDto toDto(Period period) {
        return new PeriodDto(
                period.getId(),
                period.getPeriodNumber(),
                period.getStartTime().format(TIME_FORMAT),
                period.getEndTime().format(TIME_FORMAT),
                period.getLabel()
        );
    }

    public static BreakDto toDto(BreakPeriod breakPeriod) {
        return new BreakDto(
                breakPeriod.getId(),
                breakPeriod.getName(),
                breakPeriod.getAfterPeriod(),
                breakPeriod.getDurationMinutes(),
                breakPeriod.getSortOrder(),
                breakPeriod.isBeforePeriodOne(),
                breakPeriod.isSlotable(),
                breakPeriod.getStartTime() != null ? breakPeriod.getStartTime().format(TIME_FORMAT) : null,
                breakPeriod.getEndTime() != null ? breakPeriod.getEndTime().format(TIME_FORMAT) : null
        );
    }

    public static RoomDto toDto(Room room) {
        return new RoomDto(
                room.getId(),
                room.getCode(),
                room.getName(),
                room.getType(),
                room.getCapacity()
        );
    }

    public static Room toEntity(RoomDto dto) {
        Room room = new Room();
        room.setId(dto.id());
        room.setCode(dto.code());
        room.setName(dto.name());
        room.setType(dto.type());
        room.setCapacity(dto.capacity());
        return room;
    }

    public static TeacherAvailabilityDto toDto(TeacherAvailability availability, String teacherName) {
        return new TeacherAvailabilityDto(
                availability.getId(),
                availability.getTeacherId(),
                teacherName,
                availability.getDayOfWeek(),
                availability.getPeriodNumber(),
                availability.isAvailable()
        );
    }
}
