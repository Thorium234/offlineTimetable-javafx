package com.thorium.application.port;

import com.thorium.domain.model.Room;

import java.util.List;
import java.util.Optional;

public interface RoomRepository {

    Room save(Room room);

    Optional<Room> findById(Long id);

    List<Room> findAll();

    void deleteById(Long id);

    long count();
}
