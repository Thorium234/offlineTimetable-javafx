package com.thorium.application.usecase.room;

import com.thorium.application.dto.RoomDto;
import com.thorium.application.mapper.EntityMapper;
import com.thorium.application.port.RoomRepository;

import java.util.List;

public class RoomManagementUseCase {

    private final RoomRepository roomRepository;

    public RoomManagementUseCase(RoomRepository roomRepository) {
        this.roomRepository = roomRepository;
    }

    public RoomDto create(RoomDto dto) {
        validate(dto);
        var room = EntityMapper.toEntity(dto);
        return EntityMapper.toDto(roomRepository.save(room));
    }

    public RoomDto update(RoomDto dto) {
        if (dto.id() == null) {
            throw new IllegalArgumentException("Room id is required for update");
        }
        validate(dto);
        return EntityMapper.toDto(roomRepository.save(EntityMapper.toEntity(dto)));
    }

    public void delete(Long id) {
        roomRepository.deleteById(id);
    }

    public List<RoomDto> findAll() {
        return roomRepository.findAll().stream().map(EntityMapper::toDto).toList();
    }

    private void validate(RoomDto dto) {
        if (dto.code() == null || dto.code().isBlank()) {
            throw new IllegalArgumentException("Room code is required");
        }
        if (dto.name() == null || dto.name().isBlank()) {
            throw new IllegalArgumentException("Room name is required");
        }
        if (dto.type() == null) {
            throw new IllegalArgumentException("Room type is required");
        }
        if (dto.capacity() <= 0) {
            throw new IllegalArgumentException("Capacity must be positive");
        }
    }
}
