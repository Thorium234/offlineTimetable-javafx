package com.thorium.application.dto;

import com.thorium.domain.model.RoomType;

public record RoomDto(
        Long id,
        String code,
        String name,
        RoomType type,
        int capacity
) {
}
