package com.thorium.domain.model;

import java.util.Objects;

public class Room {

    private Long id;
    private String code;
    private String name;
    private RoomType type;
    private int capacity;

    public Room() {
        this.capacity = 30;
        this.type = RoomType.CLASSROOM;
    }

    public Room(Long id, String code, String name, RoomType type, int capacity) {
        this.id = id;
        this.code = code;
        this.name = name;
        this.type = type;
        this.capacity = capacity;
    }

    public Long getId() { return id; }

    public void setId(Long id) { this.id = id; }

    public String getCode() { return code; }

    public void setCode(String code) { this.code = code; }

    public String getName() { return name; }

    public void setName(String name) { this.name = name; }

    public RoomType getType() { return type; }

    public void setType(RoomType type) { this.type = type; }

    public int getCapacity() { return capacity; }

    public void setCapacity(int capacity) { this.capacity = capacity; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Room room)) return false;
        return Objects.equals(id, room.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
